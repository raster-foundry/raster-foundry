package com.azavea.rf.batch.export.airflow

import com.azavea.rf.batch._
import com.azavea.rf.batch.export.json.S3ExportStatus
import com.azavea.rf.batch.util._
import com.azavea.rf.database.tables._
import com.azavea.rf.database.{Database => DB}
import com.azavea.rf.datamodel._

import cats._
import cats.data._
import cats.implicits._
import io.circe.parser.decode

import java.util.UUID

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util._
import scala.io.Source

case class CheckExportStatus(exportId: UUID, statusBucket: String = "rasterfoundry-dataproc-export-status-us-east-1", time: Duration = 60.minutes, region: Option[String] = None)
                            (implicit val database: DB) extends Job {
  val name = CreateExportDef.name

  /** Get S3 client per each call */
  def s3Client = S3(region = region)

  def updateExportStatus(export: Export, status: ExportStatus): Export =
    export.copy(exportStatus = status)

  def run: Unit = {
    logger.info(s"Checking export ${exportId} process...")
    val json =
      try {
        retry(time, 30.seconds) {
          Source
            .fromInputStream(s3Client.getObject(statusBucket, exportId.toString).getObjectContent)
            .getLines
            .mkString(" ")
        }
      } catch {
        case e: Throwable =>
          logger.error(e.stackTraceString)
          sendError(e.stackTraceString)
          stop
          sys.exit(1)
      }

    val s3ExportStatus =
      decode[S3ExportStatus](json) match {
        case Right(r) => r
        case _ => {
          logger.error("Incorrect S3ExportStatus JSON")
          sys.exit(1)
        }
      }

    val result = for {
      user <- fromOptionF[Future, String, User](Users.getUserById(airflowUser), "DB: Failed to fetch User.")
      export <- fromOptionF[Future, String, Export](database.db.run(Exports.getExport(exportId, user)), "DB: Failed to fetch Export.")
      exportStatus <- EitherT.right[Future, String, Int](
        database.db.run(
          Exports.updateExport(
            updateExportStatus(export, s3ExportStatus.exportStatus),
            exportId,
            user
          )
        )
      )
    } yield exportStatus

    result.value.onComplete {
      case Success(Left(e)) => {
        logger.error(e)
        sendError(e)
        stop
        sys.exit(1)
      }
      case Success(_) if s3ExportStatus.exportStatus == ExportStatus.Failed => {
        val msg = s"Export finished with ${ExportStatus.Failed}"
        logger.info(msg)
        sendError(msg)
        stop
        sys.exit(1)
      }
      case Success(_) => {
        logger.info("Export updated successfully")
        stop
      }
      case Failure(e) => {
        logger.info("Export job status set to Failed")
        logger.error(e.stackTraceString)
        sendError(e)
        stop
        sys.exit(1)
      }
    }
  }
}

object CheckExportStatus {
  val name = "check_export_status"

  def main(args: Array[String]): Unit = {
    implicit val db = DB.DEFAULT

    val job = args.toList match {
      case List(exportId, statusBucket, duration, region) => CheckExportStatus(UUID.fromString(exportId), statusBucket, Duration(duration), Some(region))
      case List(exportId, statusBucket, duration) => CheckExportStatus(UUID.fromString(exportId), statusBucket, Duration(duration))
      case List(exportId, statusBucket) => CheckExportStatus(UUID.fromString(exportId), statusBucket)
      case List(exportId) => CheckExportStatus(UUID.fromString(exportId))
      case _ =>
        throw new IllegalArgumentException("Argument could not be parsed to UUID")
    }

    job.run
  }
}
