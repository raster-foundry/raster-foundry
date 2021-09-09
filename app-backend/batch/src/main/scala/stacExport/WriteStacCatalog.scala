package com.rasterfoundry.batch.stacExport

import com.rasterfoundry.batch.Job
import com.rasterfoundry.batch.groundwork.DbIO
import com.rasterfoundry.batch.stacExport.v2.CampaignStacExport
import com.rasterfoundry.batch.util.conf.Config
import com.rasterfoundry.common.{RollbarNotifier, S3}
import com.rasterfoundry.database._
import com.rasterfoundry.database.util.RFTransactor
import com.rasterfoundry.datamodel._
import com.rasterfoundry.notification.intercom.Model.{ExternalId, Message}
import com.rasterfoundry.notification.intercom.{
  IntercomConversation,
  IntercomNotifier,
  LiveIntercomNotifier
}

import better.files.{File => ScalaFile}
import cats.data.Nested
import cats.effect._
import cats.implicits._
import com.amazonaws.services.s3.AmazonS3URI
import com.amazonaws.services.s3.model.PutObjectResult
import doobie._
import doobie.hikari.HikariTransactor
import doobie.implicits._

import java.sql.Timestamp
import java.time.{Duration, Instant}
import java.util.UUID

final case class WriteStacCatalog(
    exportId: UUID,
    notifier: IntercomNotifier[IO],
    xa: Transactor[IO]
)(implicit cs: ContextShift[IO])
    extends Config
    with RollbarNotifier {

  implicitly[Async[IO]]

  private val dbIO = new DbIO(xa);

  private val s3Client = S3()

  private def putToS3(path: String, file: ScalaFile): IO[PutObjectResult] =
    IO {
      logger.debug(s"Writing to S3: $path")
      val uri = new AmazonS3URI(path)
      s3Client.putObject(uri.getBucket, uri.getKey, file.toJava)
    }

  private def notify(userId: ExternalId, message: Message): IO[Unit] =
    IntercomConversation.notifyIO(
      userId.underlying,
      message,
      dbIO.groundworkConfig,
      notifier,
      dbIO.getConversation,
      dbIO.insertConversation
    )

  def run(): IO[Unit] = {

    logger.info(s"Exporting STAC export for record $exportId...")

    logger.info(s"Getting STAC export data for record $exportId...")

    (for {
      exportDefinition <- StacExportDao.unsafeGetById(exportId).transact(xa)
      tempDir = ScalaFile.newTemporaryDirectory()
      _ = tempDir.deleteOnExit()
      isCogExport = Nested(exportDefinition.exportAssetTypes)
        .find(_ == ExportAssetType.COG)
        .isDefined
      currentPath = if (isCogExport) {
        s"s3://$dataBucket/stac-exports/cog-exports"
      } else { s"s3://$dataBucket/stac-exports" }
      exportPath = s"$currentPath/${exportDefinition.id}"
      _ <- StacExportDao
        .update(
          exportDefinition.copy(exportStatus = ExportStatus.Exporting),
          exportDefinition.id
        )
        .transact(xa)
      _ <- exportDefinition.campaignId traverse { campaignId =>
        new CampaignStacExport(campaignId, xa, exportDefinition).run() flatMap {
          case Some(exportData) =>
            exportData.toFileSystem(tempDir)
          case None =>
            val message = s"""
            | Your export for Campaign $campaignId has failed. This is probably not retryable
            | 
            | Please contact us for advice about what to do next.
            |""".trim.stripMargin
            notify(ExternalId(exportDefinition.owner), Message(message))
        } flatMap { _ =>
          val message = s"""
            | Your export for Campaign $campaignId is complete!
            | """.trim.stripMargin
          notify(ExternalId(exportDefinition.owner), Message(message))
        }
      }
      tempZipFile <- IO { ScalaFile.newTemporaryFile("catalog", ".zip") }
      _ <- IO { tempDir.zipTo(tempZipFile) }
      _ <- putToS3(
        s"$exportPath/catalog.zip",
        tempZipFile
      )
      _ <- {
        val updatedExport =
          exportDefinition.copy(
            exportStatus = ExportStatus.Exported,
            exportLocation = Some(exportPath),
            expiration = if (isCogExport) {
              Some(Timestamp.from(Instant.now.plus(Duration.ofDays(30L))))
            } else { None }
          )
        StacExportDao.update(updatedExport, exportDefinition.id).transact(xa)
      }
    } yield ()).attempt flatMap {
      case Right(_) =>
        IO.unit

      case Left(_: IllegalArgumentException) =>
        IO.unit

      case Left(err) =>
        logger.error(s"Error occurred in processing:\n$err", err)
        for {
          exportDef <- StacExportDao.getById(exportId).transact(xa)
          owner = exportDef map { _.owner }
          message = Message(
            s"""
                | Something went wrong while processing your export ${exportId}.
                | This is probably not retryable. Please contact us for advice
                | about what to do next.
                |""".trim.stripMargin
          )
          _ <- owner traverse { userId =>
            notify(ExternalId(userId), message)
          }

        } yield ()

    }
  }

}

object WriteStacCatalog extends Job {
  val name = "write_stac_catalog"

  def runJob(args: List[String]): IO[Unit] = {
    RFTransactor.xaResource.use(transactor => {
      val notifier = new LiveIntercomNotifier[IO]
      implicit val xa: HikariTransactor[IO] = transactor

      val job = args match {
        case List(id: String) =>
          WriteStacCatalog(UUID.fromString(id), notifier, xa)
      }
      job.run()
    })
  }
}
