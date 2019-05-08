package com.rasterfoundry.batch.export

import com.rasterfoundry.batch._
import com.rasterfoundry.batch.util._
import com.rasterfoundry.batch.util.conf.Config
import com.rasterfoundry.common.RollbarNotifier
import com.rasterfoundry.common.S3
import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.database.util.RFTransactor
import com.rasterfoundry.database.{ExportDao, UserDao}
import com.rasterfoundry.datamodel._

import cats.effect.IO
import doobie._
import doobie.implicits._
import doobie.util.transactor.Transactor
import io.circe._
import io.circe.syntax._

import java.util.UUID

final case class CreateExportDef(exportId: UUID, bucket: String, key: String)(
    implicit xa: Transactor[IO])
    extends RollbarNotifier
    with Config {
  val name = CreateExportDef.name

  /** Get S3 client per each call */
  def s3Client = S3()

  @SuppressWarnings(Array("CatchThrowable"))
  protected def writeExportDefToS3(exportDef: Json,
                                   bucket: String,
                                   key: String): Unit = {
    val id = {
      val cursor = exportDef.hcursor
      cursor.downField("id").as[String]
    }
    logger.info(
      s"Uploading export definition ${id.toString} to S3 at s3://${bucket}/${key}")

    try {
      s3Client.putObjectString(bucket, key, exportDef.noSpaces)
      ()
    } catch {
      case e: Throwable => {
        logger.error(
          s"Failed to put export definition ${id} => ${bucket}/${key}")
        throw e
      }
    }
  }

  def run(): Unit = {
    val exportDefinitionWrite: ConnectionIO[Unit] = for {
      user <- UserDao.unsafeGetUserById(systemUser)
      export <- ExportDao.query.filter(exportId).select
      exportDef <- ExportDao.getExportDefinition(export)
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
