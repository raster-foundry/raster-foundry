package com.azavea.rf.batch.export

import java.util.UUID

import cats.effect.IO
import cats.implicits._
import com.azavea.rf.batch._
import com.azavea.rf.batch.util._
import com.azavea.rf.database.Implicits._
import com.azavea.rf.database.util.RFTransactor
import com.azavea.rf.database.{ExportDao, UserDao}
import com.azavea.rf.datamodel._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.transactor.Transactor
import io.circe.syntax._

final case class CreateExportDef(exportId: UUID, bucket: String, key: String)(
    implicit val xa: Transactor[IO])
    extends Job {
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
      updatedExport = export.copy(
        exportStatus = ExportStatus.Exporting,
        exportOptions = exportDef.asJson
      )
      x <- ExportDao.update(updatedExport, exportId, user)
    } yield {
      writeExportDefToS3(exportDef, bucket, key)
      logger.info(s"Wrote export definition: ${x}")
      stop
      sys.exit(0)
    }

    exportDefinitionWrite
      .transact(xa)
      .attempt
      .handleErrorWith(
        (error: Throwable) => {
          logger.error(error.stackTraceString)
          sendError(error)
          stop
          sys.exit(1)
        }
      )
      .unsafeRunSync
  }
}

object CreateExportDef {
  val name = "create_export_def"

  def main(args: Array[String]): Unit = {
    implicit val xa = RFTransactor.xa

    val job = args.toList match {
      case List(exportId, bucket, key) =>
        CreateExportDef(UUID.fromString(exportId), bucket, key)
      case _ =>
        throw new IllegalArgumentException(
          "Argument could not be parsed to UUID and URI")
    }

    job.run
  }
}
