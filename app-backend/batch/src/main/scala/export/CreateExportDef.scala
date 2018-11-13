package com.rasterfoundry.batch.export

import java.util.UUID

import cats.effect.IO
import cats.implicits._
import com.rasterfoundry.batch._
import com.rasterfoundry.batch.util._
import com.rasterfoundry.batch.util.conf.Config
import com.rasterfoundry.common.RollbarNotifier
import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.database.util.RFTransactor
import com.rasterfoundry.database.{ExportDao, UserDao}
import com.rasterfoundry.datamodel._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.transactor.Transactor
import io.circe.syntax._

final case class CreateExportDef(exportId: UUID, bucket: String, key: String)(
    implicit xa: Transactor[IO])
    extends RollbarNotifier
    with Config {
  val name = CreateExportDef.name

  /** Get S3 client per each call */
  def s3Client = S3(region = None)

  @SuppressWarnings(Array("CatchThrowable"))
  protected def writeExportDefToS3(exportDef: ExportDefinition,
                                   bucket: String,
                                   key: String): Unit = {
    logger.info(
      s"Uploading export definition ${exportDef.id.toString} to S3 at s3://${bucket}/${key}")

    try {
      s3Client.putObject(bucket, key, exportDef.asJson.toString)
    } catch {
      case e: Throwable => {
        logger.error(
          s"Failed to put export definition ${exportDef.id} => ${bucket}/${key}")
        throw e
      }
    }
  }

  def run(): Unit = {
    val exportDefinitionWrite: ConnectionIO[Unit] = for {
      user <- UserDao.unsafeGetUserById(systemUser)
      _ <- logger
        .debug(s"Fetched user successfully: ${user.id}")
        .pure[ConnectionIO]
      export <- ExportDao.query.filter(exportId).select
      _ <- logger
        .debug(s"Fetched export successfully: ${export.id}")
        .pure[ConnectionIO]
      exportDef <- ExportDao.getExportDefinition(export, user)
      _ <- logger.debug("Got export definition").pure[ConnectionIO]
      updatedExport = export.copy(
        exportStatus = ExportStatus.Exporting,
        exportOptions = exportDef.asJson
      )
      x <- ExportDao.update(updatedExport, exportId, user)
    } yield {
      logger.info(s"Writing export definition to s3")
      writeExportDefToS3(exportDef, bucket, key)
      logger.info(s"Wrote export definition: ${x}")
    }

    exportDefinitionWrite
      .transact(xa)
      .unsafeRunSync
  }
}

object CreateExportDef extends Job with RollbarNotifier {
  val name = "create_export_def"

  def runJob(args: List[String]): IO[Unit] = {
    RFTransactor.xaResource
      .use(xa => {
        implicit val transactor = xa
        val job = args match {
          case List(exportId, bucket, key) =>
            CreateExportDef(UUID.fromString(exportId), bucket, key)
          case _ =>
            throw new IllegalArgumentException(
              "Argument could not be parsed to UUID and URI")
        }

        IO { job.run() } handleErrorWith { (error: Throwable) =>
          {
            IO {
              logger.error(error.stackTraceString)
              sendError(error)
            }
          }
        }
      })
  }
}
