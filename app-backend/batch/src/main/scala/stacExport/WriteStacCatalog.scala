package com.rasterfoundry.batch.stacExport

import com.rasterfoundry.batch.Job
import com.rasterfoundry.batch.groundwork.{Config => GroundworkConfig}
import com.rasterfoundry.batch.util.conf.Config
import com.rasterfoundry.common.RollbarNotifier
import com.rasterfoundry.database._
import com.rasterfoundry.database.util.RFTransactor
import com.rasterfoundry.datamodel._
import com.rasterfoundry.notification.intercom.Model.{ExternalId, Message}
import com.rasterfoundry.notification.intercom.{
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
)(
    implicit val xa: Transactor[IO],
    cs: ContextShift[IO]
) extends Config
    with RollbarNotifier {

  private def notify(userId: ExternalId, message: Message): IO[Unit] =
    notifier.notifyUser(
      GroundworkConfig.intercomToken,
      GroundworkConfig.intercomAdminId,
      userId,
      message
    )

  private def processLayerCollection(
      exportDef: StacExport,
      exportPath: String,
      catalog: StacCatalog,
      tempDir: ScalaFile,
      layerId: UUID,
      sceneTaskAnnotation: ExportData
  ): IO[List[ScalaFile]] = {
    logger.info(s"Processing Layer Collection: $layerId")
    val layerCollectionPrefix = s"$exportPath/layer-collection"
    val labelRootURI = new URI(layerCollectionPrefix)
    val layerCollectionAbsolutePath =
      s"$layerCollectionPrefix/collection.json"
    val layerCollection = Utils.getLayerStacCollection(
      exportDef,
      catalog,
      layerId,
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

    val sceneItems = sceneTaskAnnotation.scenes flatMap { scene =>
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
      case SceneItemWithAbsolute(itemWithAbsolute, _) =>
        StacLink(
          s"./${itemWithAbsolute.item.id}.json",
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
        case SceneItemWithAbsolute(item, ingestLocation) => {
          StacFileIO.writeObjectToFilesystem(tempDir, item) <*
            StacFileIO.getObject(tempDir, item, ingestLocation)
        }
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

  def run(): IO[Unit] =
    ({

      logger.info(s"Exporting STAC export for record $exportId...")

      logger.info(s"Getting STAC export data for record $exportId...")
      val dbIO
        : ConnectionIO[(StacExport, Option[(UUID, Map[UUID, ExportData])])] =
        for {
          exportDefinition <- StacExportDao.unsafeGetById(exportId)
          _ <- StacExportDao.update(
            exportDefinition.copy(exportStatus = ExportStatus.Exporting),
            exportDefinition.id
          )
          layerSceneTaskAnnotation <- exportDefinition.annotationProjectId traverse {
            pid =>
              DatabaseIO.sceneTaskAnnotationforLayers(
                pid,
                exportDefinition.taskStatuses
              ) map { (pid, _) }
          }
        } yield (exportDefinition, layerSceneTaskAnnotation)

      logger.info(
        s"Creating content bundle with layers, images, and labels for record $exportId..."
      )

      dbIO.transact(xa) flatMap {
        case (exportDef, Some((annotationProjectId, layerInfoMap))) =>
          val currentPath = s"s3://$dataBucket/stac-exports"
          val exportPath = s"$currentPath/${exportDef.id}"
          logger.info(s"Writing export under prefix: $exportPath")
          val layerIds = layerInfoMap.keys.toList
          val catalog: StacCatalog =
            Utils.getStacCatalog(exportDef, "1.0.0-beta.1", layerIds)
          val catalogWithPath =
            ObjectWithAbsolute(s"$exportPath/catalog.json", catalog)

          val tempDir = ScalaFile.newTemporaryDirectory()
          tempDir.deleteOnExit()

          val layerIO = layerInfoMap.toList traverse {
            case (layerId, sceneTaskAnnotation) =>
              processLayerCollection(
                exportDef,
                exportPath,
                catalog,
                tempDir,
                layerId,
                sceneTaskAnnotation
              )
          }
          for {
            _ <- StacFileIO.writeObjectToFilesystem(tempDir, catalogWithPath)
            _ <- layerIO
            tempZipFile <- IO { ScalaFile.newTemporaryFile("catalog", ".zip") }
            _ <- IO { tempDir.zipTo(tempZipFile) }
            _ <- StacFileIO.putToS3(
              s"$exportPath/catalog.zip",
              tempZipFile
            )
            _ <- {
              val updatedExport =
                exportDef.copy(
                  exportStatus = ExportStatus.Exported,
                  exportLocation = Some(exportPath)
                )
              StacExportDao.update(updatedExport, exportDef.id).transact(xa)
            }
            _ <- AnnotationProjectDao
              .unsafeGetById(annotationProjectId)
              .transact(xa) map { projectName =>
              val message = Message(s"""
              | Your STAC export for project ${projectName} has completed!
              | You can see exports for your project at
              | ${GroundworkConfig.groundworkUrlBase}/app/projects/${annotationProjectId}/exports 
              """.trim.stripMargin)
              notify(ExternalId(exportDef.owner), message)
            }
          } yield ()
        case (exportDef, _) =>
          val message = Message(
            """
        | Somehow you had an export without an associated annotation project.
        | This shouldn't happen. Please reply to this message to let us know
        | how you got here.
        """.trim.stripMargin
          )
          notify(ExternalId(exportDef.owner), message) *>
            IO {
              val msg = "Export definition is missing an annotation project ID"
              logger.error(msg)
              throw new IllegalArgumentException(msg)
            }

      }
    }).attempt flatMap {
      case Right(_) =>
        IO.unit

      case Left(_: IllegalArgumentException) =>
        IO.unit

      case Left(_) =>
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
