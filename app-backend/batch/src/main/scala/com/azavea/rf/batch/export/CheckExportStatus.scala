package com.azavea.rf.batch.export

import java.util.UUID

import cats.data._
import cats.effect.IO
import cats.implicits._
import com.azavea.rf.batch._
import com.azavea.rf.batch.export.json.S3ExportStatus
import com.azavea.rf.batch.util._
import com.azavea.rf.datamodel._
import io.circe.parser.decode

import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import doobie.util.transactor.Transactor

import com.azavea.rf.database.Implicits._
import com.azavea.rf.database.util.RFTransactor

import scala.concurrent.duration._
import scala.io.Source
import scala.util._

case class CheckExportStatus(exportId: UUID, statusBucket: String = "rasterfoundry-dataproc-export-status-us-east-1", time: Duration = 60.minutes, region: Option[String] = None)(implicit val xa: Transactor[IO]) extends Job {
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

    def updateIo(exportId: UUID, exportStatus: ExportStatus): ConnectionIO[ExportStatus] =
      sql"update exports set export_status = ${exportStatus} where id = ${exportId}"
        .update.withUniqueGeneratedKeys("export_status")

    val withLoggingUpdateExportStatus: ConnectionIO[Unit] =
      updateIo(exportId, s3ExportStatus.exportStatus) map {
        case ExportStatus.Failed => {
          logger.info(s"Export finished with ${s3ExportStatus.exportStatus}")
          sendError(s"Export status update failed for ${exportId}")
        }
        case ExportStatus.Exported => {
          logger.info(s"Export updated successfully")
        }
        case _ =>
          logger.info(s"Export ${exportId} has not yet completed: ${s3ExportStatus.exportStatus}")
      }

    withLoggingUpdateExportStatus.transact(xa).unsafeRunSync
  }
}

object CheckExportStatus {
  val name = "check_export_status"
  implicit val xa = RFTransactor.xa

  def main(args: Array[String]): Unit = {

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
