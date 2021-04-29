package com.rasterfoundry.batch.stacExport

import com.rasterfoundry.batch.Job
import com.rasterfoundry.batch.groundwork.{Config => GroundworkConfig, DbIO}
import com.rasterfoundry.batch.stacExport.v2.CampaignStacExport
import com.rasterfoundry.batch.util.conf.Config
import com.rasterfoundry.common.RollbarNotifier
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
import cats.effect.{ContextShift, IO}
import cats.implicits._
import com.azavea.stac4s._
import doobie._
import doobie.hikari.HikariTransactor
import doobie.implicits._
import sttp.client.asynchttpclient.cats.AsyncHttpClientCatsBackend

import java.net.URI
import java.util.UUID

final case class WriteStacCatalog(
    exportId: UUID,
    notifier: IntercomNotifier[IO]
)(implicit val xa: Transactor[IO], cs: ContextShift[IO])
    extends Config
    with RollbarNotifier {

  private val dbIO = new DbIO(xa);

  private def notify(userId: ExternalId, message: Message): IO[Unit] =
    IntercomConversation.notifyIO(
      userId.underlying,
      message,
      dbIO.groundworkConfig,
      notifier,
      dbIO.getConversation,
      dbIO.insertConversation
    )

  private def processLayerCollection(
      exportDef: StacExport,
      exportPath: String,
      catalog: StacCatalog,
      tempDir: ScalaFile,
      annotationProjectId: UUID,
      sceneTaskAnnotation: ExportData
  ): IO[List[ScalaFile]] = {
    logger.info(s"Processing Layer Collection: $annotationProjectId")
    val layerCollectionPrefix = s"$exportPath/layer-collection"
    val labelRootURI = new URI(layerCollectionPrefix)
    val layerCollectionAbsolutePath =
      s"$layerCollectionPrefix/collection.json"
    val layerCollection = Utils.getLayerStacCollection(
      exportDef,
      catalog,
      annotationProjectId,
      sceneTaskAnnotation
    )

    val imageCollection =
      Utils.getImagesCollection(exportDef, catalog, layerCollection)
    val labelCollection =
      Utils.getLabelCollection(exportDef, catalog, layerCollection)

    val annotations = ObjectWithAbsolute(
      s"$layerCollectionPrefix/labels/data.geojson",
      sceneTaskAnnotation.annotations
    )

    val sceneItems: List[ImageryItem] = sceneTaskAnnotation.scenes match {
      case Nil =>
        List(
          TileLayersItemWithAbsolute(
            Utils.getTileLayersItem(
              catalog,
              layerCollectionPrefix,
              imageCollection,
              sceneTaskAnnotation.tileLayers,
              sceneTaskAnnotation.taskGeomExtent
            )
          )
        )
      case scenes =>
        scenes flatMap { scene =>
          (
            Utils.getSceneItem(
              catalog,
              layerCollectionPrefix,
              imageCollection,
              scene
            ),
            scene.ingestLocation
          ).mapN(SceneItemWithAbsolute.apply _)

        }
    }

    val absoluteLayerCollection =
      ObjectWithAbsolute(
        layerCollectionAbsolutePath,
        layerCollection.copy(
          links = layerCollection.links ++ List(
            StacLink(
              "./images/collection.json",
              StacLinkType.Child,
              Some(`application/json`),
              Some(s"Images Collection: ${imageCollection.id}")
            ),
            StacLink(
              "./labels/collection.json",
              StacLinkType.Child,
              Some(`application/json`),
              Some(s"Label Collection: ${labelCollection.id}")
            )
          )
        )
      )

    val updatedSceneLinks = imageCollection.links ++ sceneItems.map {
      imageryItem =>
        StacLink(
          s"./${imageryItem.item.item.id}.json",
          StacLinkType.Item,
          Some(`application/json`),
          None
        )
    }

    val updatedSceneCollection: StacCollection =
      imageCollection.copy(links = updatedSceneLinks)

    val imageCollectionWithPath = ObjectWithAbsolute(
      s"$layerCollectionPrefix/images/collection.json",
      updatedSceneCollection
    )
    val labelItem =
      Utils.getLabelItem(
        catalog,
        sceneTaskAnnotation,
        labelCollection,
        sceneItems map { _.item },
        s"$layerCollectionPrefix/labels",
        labelRootURI
      )

    val updatedLabelLinks = StacLink(
      s"./${labelItem.item.id}.json",
      StacLinkType.Item,
      Some(`application/json`),
      None
    ) :: labelCollection.links

    val labelCollectionWithPath = ObjectWithAbsolute(
      s"$layerCollectionPrefix/labels/collection.json",
      labelCollection.copy(links = updatedLabelLinks)
    )

    for {
      localLabelCollectionResult <- StacFileIO.writeObjectToFilesystem(
        tempDir,
        labelCollectionWithPath
      )
      localSceneItemResults <- sceneItems.parTraverse {
        case SceneItemWithAbsolute(item, ingestLocation) =>
          StacFileIO.signTiffAsset(item.item, ingestLocation) flatMap {
            withSignedUrl =>
              StacFileIO.writeObjectToFilesystem(
                tempDir,
                item.copy(item = withSignedUrl)
              )
          }
        case TileLayersItemWithAbsolute(item) =>
          StacFileIO.writeObjectToFilesystem(
            tempDir,
            item
          )
      }
      localSceneCollectionResults <- StacFileIO.writeObjectToFilesystem(
        tempDir,
        imageCollectionWithPath
      )
      localLabelItemResults <- StacFileIO.writeObjectToFilesystem(
        tempDir,
        labelItem
      )
      localLayerCollectionResults <- StacFileIO.writeObjectToFilesystem(
        tempDir,
        absoluteLayerCollection
      )
      localAnnotationResults <- StacFileIO.writeObjectToFilesystem(
        tempDir,
        annotations
      )

    } yield {
      List(
        localLabelCollectionResult,
        localSceneCollectionResults,
        localLabelItemResults,
        localAnnotationResults,
        localLayerCollectionResults
      ) ++ localSceneItemResults
    }

  }

  def runAnnotationProject(
      exportDefinition: StacExport,
      annotationProjectId: UUID,
      tempDir: ScalaFile,
      exportPath: String
  ): IO[Unit] =
    (for {
      layerInfoMap <- DatabaseIO
        .sceneTaskAnnotationforLayers(
          annotationProjectId,
          exportDefinition.taskStatuses
        )
        .transact(xa)
      _ = logger.info(s"Writing export under prefix: $exportPath")
      catalog = Utils.getAnnotationProjectStacCatalog(
        exportDefinition,
        "1.0.0-beta.1",
        annotationProjectId
      )
      catalogWithPath = ObjectWithAbsolute(
        s"$exportPath/catalog.json",
        catalog
      )
      _ <- StacFileIO.writeObjectToFilesystem(tempDir, catalogWithPath)
      _ <- layerInfoMap traverse { exportData =>
        processLayerCollection(
          exportDefinition,
          exportPath,
          catalog,
          tempDir,
          annotationProjectId,
          exportData
        )
      }
      _ <- AnnotationProjectDao
        .unsafeGetById(annotationProjectId)
        .transact(xa) flatMap { project =>
        val message = Message(s"""
              | Your STAC export for project ${project.name} has completed!
              | You can see exports for your project at
              | ${GroundworkConfig.groundworkUrlBase}/app/projects/${annotationProjectId}/exports 
              """.trim.stripMargin)
        notify(ExternalId(exportDefinition.owner), message)
      }
    } yield ())

  def run(): IO[Unit] = {

    logger.info(s"Exporting STAC export for record $exportId...")

    logger.info(s"Getting STAC export data for record $exportId...")

    (for {
      exportDefinition <- StacExportDao.unsafeGetById(exportId).transact(xa)
      tempDir = ScalaFile.newTemporaryDirectory()
      _ = tempDir.deleteOnExit()
      currentPath = s"s3://$dataBucket/stac-exports"
      exportPath = s"$currentPath/${exportDefinition.id}"
      _ <- StacExportDao
        .update(
          exportDefinition.copy(exportStatus = ExportStatus.Exporting),
          exportDefinition.id
        )
        .transact(xa)

      _ <- exportDefinition.annotationProjectId traverse { pid =>
        runAnnotationProject(exportDefinition, pid, tempDir, exportPath)
      }
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
      _ <- StacFileIO.putToS3(
        s"$exportPath/catalog.zip",
        tempZipFile
      )
      _ <- {
        val updatedExport =
          exportDefinition.copy(
            exportStatus = ExportStatus.Exported,
            exportLocation = Some(exportPath)
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
      AsyncHttpClientCatsBackend[IO]() flatMap { backend =>
        val notifier = new LiveIntercomNotifier[IO](backend)
        implicit val xa: HikariTransactor[IO] = transactor

        val job = args match {
          case List(id: String) =>
            WriteStacCatalog(UUID.fromString(id), notifier)
        }
        job.run()
      }

    })
  }
}
