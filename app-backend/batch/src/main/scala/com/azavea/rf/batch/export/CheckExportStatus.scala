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

import doobie.util.transactor.Transactor

import scala.concurrent.Future
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
  //   logger.info(s"Checking export ${exportId} process...")
  //   val json =
  //     try {
  //       retry(time, 30.seconds) {
  //         Source
  //           .fromInputStream(s3Client.getObject(statusBucket, exportId.toString).getObjectContent)
  //           .getLines
  //           .mkString(" ")
  //       }
  //     } catch {
  //       case e: Throwable =>
  //         logger.error(e.stackTraceString)
  //         sendError(e.stackTraceString)
  //         stop
  //         sys.exit(1)
  //     }

  //   val s3ExportStatus =
  //     decode[S3ExportStatus](json) match {
  //       case Right(r) => r
  //       case _ => {
  //         logger.error("Incorrect S3ExportStatus JSON")
  //         sys.exit(1)
  //       }
  //     }

  //   // This changed to OptionT because of strange compilation behavior after bumping the cats version
  //   // I'd like to investigate further, but it looks like it might be a bug
  //   val result = for {
  //     user <- OptionT[Future, User](Users.getUserById(systemUser))
  //     export <- OptionT[Future, Export](database.db.run(Exports.getExport(exportId, user)))
  //     exportStatus <- OptionT.liftF({
  //       database.db.run(
  //         Exports.updateExport(
  //           updateExportStatus(export, s3ExportStatus.exportStatus),
  //           exportId,
  //           user
  //         )
  //       )
  //     })
  //   } yield exportStatus

  //   result.value.onComplete {
  //     case Success(None) => {
  //       logger.error("DB: Failed to fetch user or export")
  //       sendError("DB: Failed to fetch user or export")
  //       stop
  //       sys.exit(1)
  //     }
  //     case Success(_) if s3ExportStatus.exportStatus == ExportStatus.Failed => {
  //       val msg = s"Export finished with ${ExportStatus.Failed}"
  //       logger.info(msg)
  //       sendError(msg)
  //       stop
  //       sys.exit(1)
  //     }
  //     case Success(_) => {
  //       logger.info("Export updated successfully")
  //       stop
  //     }
  //     case Failure(e) => {
  //       logger.info("Export job status set to Failed")
  //       logger.error(e.stackTraceString)
  //       sendError(e)
  //       stop
  //       sys.exit(1)
  //     }
  //   }
  // }
  }
}

object CheckExportStatus {
  val name = "check_export_status"

  def main(args: Array[String]): Unit = {}

  //   val job = args.toList match {
  //     case List(exportId, statusBucket, duration, region) => CheckExportStatus(UUID.fromString(exportId), statusBucket, Duration(duration), Some(region))
  //     case List(exportId, statusBucket, duration) => CheckExportStatus(UUID.fromString(exportId), statusBucket, Duration(duration))
  //     case List(exportId, statusBucket) => CheckExportStatus(UUID.fromString(exportId), statusBucket)
  //     case List(exportId) => CheckExportStatus(UUID.fromString(exportId))
  //     case _ =>
  //       throw new IllegalArgumentException("Argument could not be parsed to UUID")
  //   }

  //   job.run
  // }
}
