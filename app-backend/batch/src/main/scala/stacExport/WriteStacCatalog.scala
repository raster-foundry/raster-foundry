package com.rasterfoundry.batch.stacExport

import com.rasterfoundry.batch.Job
import com.rasterfoundry.batch.util.conf.Config
import com.rasterfoundry.common.RollbarNotifier
import com.rasterfoundry.database._
import com.rasterfoundry.database.util.RFTransactor
import com.rasterfoundry.datamodel._

import better.files.{File => ScalaFile}
import cats.effect.{ContextShift, IO}
import cats.implicits._
import cats.implicits._
import com.amazonaws.services.s3.model.PutObjectResult
import doobie._
import doobie.hikari.HikariTransactor
import doobie.implicits._
import geotrellis.server.stac._

import java.net.URI
import java.util.UUID

final case class WriteStacCatalog(exportId: UUID)(
    implicit val xa: Transactor[IO],
    cs: ContextShift[IO]
) extends Config
    with RollbarNotifier {

  private def processLayerCollection(
      exportDef: StacExport,
      exportPath: String,
      catalog: StacCatalog,
      tempDir: ScalaFile,
      layerId: UUID,
      sceneTaskAnnotation: ExportData
  ): IO[(List[PutObjectResult], List[ScalaFile])] = {
    logger.info(s"Processing Layer Collection: $layerId")
    val layerCollectionPrefix = s"$exportPath/$layerId"
    val labelRootURI = new URI(layerCollectionPrefix)
    val layerCollectionAbsolutePath =
      s"$layerCollectionPrefix/collection.json"
    val layerCollection = Utils.getLayerStacCollection(
      exportDef,
      catalog,
      layerId,
      sceneTaskAnnotation
    )

    val sceneCollection =
      Utils.getSceneCollection(exportDef, catalog, layerCollection)
    val labelCollection =
      Utils.getLabelCollection(exportDef, catalog, layerCollection)

    val annotations = ObjectWithAbsolute(
      s"$layerCollectionPrefix/${labelCollection.id}/data.geojson",
      sceneTaskAnnotation.annotations
    )

    val sceneItems = sceneTaskAnnotation.scenes flatMap { scene =>
      Utils.getSceneItem(catalog, layerCollectionPrefix, sceneCollection, scene)
    }

    val absoluteLayerCollection =
      ObjectWithAbsolute(
        layerCollectionAbsolutePath,
        layerCollection.copy(
          links = layerCollection.links ++ List(
            StacLink(
              s"./${sceneCollection.id}/collection.json",
              Child,
              Some(`application/json`),
              Some(s"Scene Collection: ${sceneCollection.id}"),
              List()
            ),
            StacLink(
              s"./${labelCollection.id}/collection.json",
              Child,
              Some(`application/json`),
              Some(s"Label Collection: ${labelCollection.id}"),
              List()
            )
          )
        )
      )

    val updatedSceneLinks = sceneCollection.links ++ sceneItems.map {
      itemWithAbsolute =>
        StacLink(
          s"./${itemWithAbsolute.item.id}.json",
          Item,
          Some(`application/json`),
          None,
          List()
        )
    }

    val updatedSceneCollection: StacCollection =
      sceneCollection.copy(links = updatedSceneLinks)

    val sceneCollectionWithPath = ObjectWithAbsolute(
      s"$layerCollectionPrefix/${sceneCollection.id}/collection.json",
      updatedSceneCollection
    )
    val labelItem =
      Utils.getLabelItem(
        catalog,
        sceneTaskAnnotation,
        labelCollection,
        sceneItems,
        s"$layerCollectionPrefix/${labelCollection.id}",
        labelRootURI
      )

    val updatedLabelLinks = StacLink(
      s"./${labelItem.item.id}.json",
      Item,
      Some(`application/json`),
      None,
      List()
    ) :: labelCollection.links

    val labelCollectionWithPath = ObjectWithAbsolute(
      s"$layerCollectionPrefix/${labelCollection.id}/collection.json",
      labelCollection.copy(links = updatedLabelLinks)
    )
    for {
      s3LabelCollectionResult <- StacFileIO.putObjectToS3(
        labelCollectionWithPath
      )
      s3SceneItemResults <- sceneItems.parTraverse(
        item => StacFileIO.putObjectToS3(item)
      )
      s3SceneCollectionResults <- StacFileIO.putObjectToS3(
        sceneCollectionWithPath
      )
      s3LabelItemResults <- StacFileIO.putObjectToS3(labelItem)
      s3LayerCollectionResults <- StacFileIO.putObjectToS3(
        absoluteLayerCollection
      )
      s3AnnotationResults <- StacFileIO.putObjectToS3(annotations)

      localLabelCollectionResult <- StacFileIO.writeObjectToFilesystem(
        tempDir,
        labelCollectionWithPath
      )
      localSceneItemResults <- sceneItems.parTraverse(
        item => StacFileIO.writeObjectToFilesystem(tempDir, item)
      )
      localSceneCollectionResults <- StacFileIO.writeObjectToFilesystem(
        tempDir,
        sceneCollectionWithPath
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
      (
        List(
          s3LabelCollectionResult,
          s3SceneCollectionResults,
          s3LabelItemResults,
          s3AnnotationResults,
          s3LayerCollectionResults
        ) ++ s3SceneItemResults,
        List(
          localLabelCollectionResult,
          localSceneCollectionResults,
          localLabelItemResults,
          localAnnotationResults,
          localLayerCollectionResults
        ) ++ localSceneItemResults
      )
    }
  }

  def run(): IO[Unit] = {

    logger.info(s"Exporting STAC export for record $exportId...")

    logger.info(s"Getting STAC export data for record $exportId...")
    val dbIO = for {
      exportDefinition <- StacExportDao.unsafeGetById(exportId)
      _ <- StacExportDao.update(
        exportDefinition.copy(exportStatus = ExportStatus.Exporting),
        exportDefinition.id
      )
      layerSceneTaskAnnotation <- DatabaseIO.sceneTaskAnnotationforLayers(
        exportDefinition.layerDefinitions,
        exportDefinition.taskStatuses
      )
    } yield (exportDefinition, layerSceneTaskAnnotation)

    logger.info(
      s"Creating content bundle with layers, scenes, and labels for record $exportId..."
    )

    dbIO.transact(xa) flatMap {
      case (exportDef, layerInfo) =>
        val currentPath = s"s3://$dataBucket/stac-exports"
        val exportPath = s"$currentPath/${exportDef.id}"
        logger.info(s"Writing export under prefix: $exportPath")
        val layerIds = layerInfo.keys.toList
        val catalog: StacCatalog =
          Utils.getStacCatalog(currentPath, exportDef, "0.8.0", layerIds)
        val catalogWithPath =
          ObjectWithAbsolute(s"$exportPath/catalog.json", catalog)

        val tempDir = ScalaFile.newTemporaryDirectory()
        tempDir.deleteOnExit()

        val layerIO = layerInfo map {
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
          s3CatalogResults <- StacFileIO.putObjectToS3(catalogWithPath)
          _ <- StacFileIO.writeObjectToFilesystem(tempDir, catalogWithPath)
          layer <- layerIO.toList.sequence
          (s3LayerCollectionResults, _) = layer.unzip
          tempZipFile <- IO { ScalaFile.newTemporaryFile("catalog", ".zip") }
          _ <- IO { tempDir.zipTo(tempZipFile) }
          s3ZipFile <- StacFileIO.putToS3(
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
        } yield {
          val totalResults =
            (s3CatalogResults :: s3ZipFile :: s3LayerCollectionResults.flatten).length
          logger.info(s"Uploaded $totalResults to S3")
          ()
        }
    }
  }

}

object WriteStacCatalog extends Job {
  val name = "write_stac_catalog"

  def runJob(args: List[String]): IO[Unit] = {
    RFTransactor.xaResource.use(transactor => {
      implicit val xa: HikariTransactor[IO] = transactor
      val job = args match {
        case List(id: String) => WriteStacCatalog(UUID.fromString(id))
      }
      job.run()
    })
  }
}
