package com.azavea.rf.batch.export

import java.util.UUID

import cats.data._
import cats.effect.IO
import cats.implicits._
import com.azavea.rf.batch._
import com.azavea.rf.batch.util._
import com.azavea.rf.database.Implicits._
import com.azavea.rf.database.util.RFTransactor
import com.azavea.rf.database.{ExportDao, UserDao}
import com.azavea.rf.datamodel._
import doobie.util.transactor.Transactor
import io.circe.syntax._
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._

import scala.util._

case class CreateExportDef(exportId: UUID, bucket: String, key: String)(implicit val xa: Transactor[IO]) extends Job {
  val name = CreateExportDef.name

  /** Get S3 client per each call */
  def s3Client = S3(region = None)

  protected def writeExportDefToS3(exportDef: ExportDefinition, bucket: String, key: String): Unit = {
    logger.info(s"Uploading export definition ${exportDef.id.toString} to S3 at s3://${bucket}/${key}")

    try {
      s3Client.putObject(bucket, key, exportDef.asJson.toString)
    } catch {
      case e: Throwable => {
        logger.error(s"Failed to put export definition ${exportDef.id} => ${bucket}/${key}")
        throw e
      }
    }
  }

  def run: Unit = {
    val exportDefinitionWrite = for {
      user <- UserDao.query.filter(fr"id = ${systemUser}").select
      export <- ExportDao.query.filter(fr"id = ${exportId}").select
      exportDef <- ExportDao.getExportDefinition(export, user)
      x <- {
        writeExportDefToS3(exportDef, bucket, key)
        val updatedExport = export.copy(
          exportStatus = ExportStatus.Exporting,
          exportOptions = exportDef.asJson
        )
        ExportDao.update(updatedExport, exportId, user)
      }
    } yield {
      logger.info(s"Wrote export definition: ${x}")
    }

    exportDefinitionWrite.transact(xa).unsafeToFuture onComplete {
      case Success(export) => {
        logger.info("Successfully wrote export definition")
        stop
        sys.exit(0)
      }
      case Failure(e) => {
        logger.error(e.stackTraceString)
        sendError(e)
        stop
        sys.exit(1)
      }
    }
  }
}

object CreateExportDef {
  val name = "create_export_def"

  def main(args: Array[String]): Unit = {
    implicit val xa = RFTransactor.xa

    val job = args.toList match {
      case List(exportId, bucket, key) => CreateExportDef(UUID.fromString(exportId), bucket, key)
      case _ =>
        throw new IllegalArgumentException("Argument could not be parsed to UUID and URI")
    }

    job.run
  }
}
