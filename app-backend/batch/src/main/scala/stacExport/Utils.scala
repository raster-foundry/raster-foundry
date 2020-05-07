package com.rasterfoundry.batch.stacExport

import com.rasterfoundry.datamodel._

import cats.implicits._
import geotrellis.proj4.CRS
import geotrellis.server.stac._
import geotrellis.vector.methods.Implicits._
import geotrellis.vector.reproject.Reproject
import io.circe._
import io.circe.syntax._

import java.net.URI
import java.sql.Timestamp
import java.util.{Date, UUID}

// TODO: Layers are no longer relevant to AnnotationProjects.
// We can simplify the exporter by having a single collection
// for all the scenes, instead of a layer collection wrapping them.
object Utils {

  private val relativeCatalogRoot = StacLink(
    "../../catalog.json",
    StacRoot,
    Some(`application/json`),
    Some("Root"),
    List()
  )

  private val relativeLayerCollection = StacLink(
    "../collection.json",
    Parent,
    Some(`application/json`),
    Some("Layer Collection"),
    List()
  )

  def getStacCatalog(
      export: StacExport,
      stacVersion: String,
      layerIds: List[UUID]
  ): StacCatalog = {
    val catalogId = export.id.toString
    val catalogDescription =
      s"Exported from Raster Foundry ${new Timestamp(new Date().getTime).toString}"
    val catalogOwnLinks = List(
      // s3://<prefix>/<catalogId>/catalog.json
      StacLink(
        "./catalog.json",
        StacRoot,
        Some(`application/json`),
        Some(s"Catalog $catalogId"),
        List()
      )
    ) ++ layerIds.map { layerId =>
      StacLink(
        s"./$layerId/collection.json",
        Child,
        Some(`application/json`),
        Some(s"Layer Collection $layerId"),
        List()
      )
    }
    StacCatalog(
      stacVersion,
      catalogId,
      None,
      catalogDescription,
      catalogOwnLinks
    )
  }

  def getLabelItem(
      catalog: StacCatalog,
      sceneTaskAnnotation: ExportData,
      labelCollection: StacCollection,
      sceneItems: List[ObjectWithAbsolute[StacItem]],
      labelCollectionPrefix: String,
      catalogRootPath: URI
  ): ObjectWithAbsolute[StacItem] = {
    val labelItemId = UUID.randomUUID().toString
    val labelItemGeomExtent = sceneTaskAnnotation.taskGeomExtent
    val labelItemFootprint = labelItemGeomExtent.geometry
    val labelItemBbox = TwoDimBbox(
      labelItemGeomExtent.xMin,
      labelItemGeomExtent.yMin,
      labelItemGeomExtent.xMax,
      labelItemGeomExtent.yMax
    )
    val absPath = labelCollectionPrefix
    // s3://<prefix>/<catalogId>/<layerCollectionId>/<labelCollectionId>/<labelItemId>.json
    val labelItemSelfAbsPath = s"$absPath/$labelItemId.json"
    val labelItemLinks = List(
      StacLink(
        "./collection.json",
        Parent,
        Some(`application/json`),
        Some("Label Collection"),
        List()
      ),
      StacLink(
        "./collection.json",
        Collection,
        Some(`application/json`),
        Some("Label Collection"),
        List()
      ),
      relativeCatalogRoot
    ) ++ sceneItems.map(item => {
      val stacItemURI = new URI(item.absolutePath)
      val relativeStacItemPath =
        s"../${catalogRootPath.relativize(stacItemURI)}"
      StacLink(
        relativeStacItemPath,
        Source,
        Some(`image/cog`),
        Some("Source image STAC item for the label item"),
        List(item.item.id)
      )
    })
    val dateTime = labelCollection.extent.temporal.interval.headOption match {
      case Some(interval) =>
        interval.value match {
          case Some(s) :: _           => Timestamp.from(s)
          case None :: Some(e) :: Nil => Timestamp.from(e)
          case _                      => new Timestamp(new java.util.Date().getTime)
        }
      case _ => new Timestamp(new java.util.Date().getTime)
    }
    val labelItemProperties = StacLabelItemProperties(
      sceneTaskAnnotation.labelItemsPropsThin.property,
      sceneTaskAnnotation.labelItemsPropsThin.classes,
      "Labels in layer",
      sceneTaskAnnotation.labelItemsPropsThin._type,
      Some(List(sceneTaskAnnotation.labelItemsPropsThin.task)),
      Some(List("manual")),
      None,
      dateTime
    )
    val labelItemPropertiesJsonObj = JsonObject.fromMap(
      Map(
        "label:property" -> labelItemProperties.property.asJson,
        "label:classes" -> labelItemProperties.classes.asJson,
        "label:description" -> labelItemProperties.description.asJson,
        "label:type" -> labelItemProperties._type.asJson,
        "label:task" -> labelItemProperties.task.asJson,
        "label:method" -> labelItemProperties.method.asJson,
        "label:overview" -> labelItemProperties.overview.asJson,
        "datetime" -> labelItemProperties.datetime.asJson
      )
    )
    val labelDataRelLink = "./data.geojson"
    val labelAsset = Map(
      labelItemId ->
        StacAsset(
          labelDataRelLink,
          Some("Label Data Feature Collection"),
          Some(`application/geo+json`)
        )
    )

    val labelItem = StacItem(
      labelItemId,
      catalog.stacVersion,
      List("label"),
      "Feature",
      labelItemFootprint,
      labelItemBbox,
      labelItemLinks,
      labelAsset,
      Some(labelCollection.id),
      labelItemPropertiesJsonObj
    )

    ObjectWithAbsolute(labelItemSelfAbsPath, labelItem)
  }

  def getSceneItem(
      catalog: StacCatalog,
      layerCollectionAbsolutePath: String,
      sceneCollection: StacCollection,
      scene: Scene
  ): Option[ObjectWithAbsolute[StacItem]] = {

    val sceneFootprintOption = scene.dataFootprint match {
      case Some(footprint) =>
        Some(
          Reproject(footprint, CRS.fromEpsgCode(3857), CRS.fromEpsgCode(4326))
        )
      case _ => None
    }
    val itemBboxOption = sceneFootprintOption.map { footprint =>
      TwoDimBbox(
        footprint.extent.xmin,
        footprint.extent.ymin,
        footprint.extent.xmax,
        footprint.extent.ymax
      )
    }

    val sceneLinks = List(
      StacLink(
        "./collection.json",
        Parent,
        Some(`application/json`),
        Some("Scene Collection"),
        List()
      ),
      StacLink(
        "./collection.json",
        Collection,
        Some(`application/json`),
        Some("Scene Collection"),
        List()
      ),
      relativeCatalogRoot
    )
    val sceneProperties = JsonObject.fromMap(
      Map(
        "datetime" -> scene.filterFields.acquisitionDate
          .getOrElse(scene.createdAt)
          .toLocalDateTime
          .toString
          .asJson
      )
    )
    val sceneAssetOption = scene.ingestLocation map { _ =>
      Map(
        scene.id.toString ->
          StacAsset(
            s"./${scene.id}.tiff",
            Some("scene"),
            Some(`image/cog`)
          )
      )
    }
    val sceneItemAbsolutePath =
      s"$layerCollectionAbsolutePath/${sceneCollection.id}/${scene.id}.json"

    (sceneFootprintOption, itemBboxOption, sceneAssetOption).tupled.map {
      case (sceneFootprint, itemBbox, sceneAsset) =>
        ObjectWithAbsolute(
          sceneItemAbsolutePath,
          StacItem(
            scene.id.toString,
            catalog.stacVersion,
            List(),
            "Feature",
            sceneFootprint,
            itemBbox,
            sceneLinks,
            sceneAsset,
            Some(sceneCollection.id),
            sceneProperties
          )
        )
    }

  }

  def getLabelCollection(
      exportDefinition: StacExport,
      catalog: StacCatalog,
      layerStacCollection: StacCollection
  ): StacCollection = {
    val labelCollectionLinks = List(
      relativeCatalogRoot,
      relativeLayerCollection
    )

    exportDefinition.createStacCollection(
      catalog.stacVersion,
      UUID.randomUUID().toString,
      Some("Label Collection"),
      s"Label Collection in layer ${layerStacCollection.id}",
      List[String](),
      "1",
      List[StacProvider](),
      layerStacCollection.extent,
      JsonObject.empty,
      labelCollectionLinks
    )
  }

  def getSceneCollection(
      exportDefinition: StacExport,
      catalog: StacCatalog,
      layerStacCollection: StacCollection
  ): StacCollection = {
    val sceneCollectionId = UUID.randomUUID().toString
    val sceneCollectionOwnLinks = List(
      relativeCatalogRoot,
      relativeLayerCollection
    )
    exportDefinition.createStacCollection(
      catalog.stacVersion,
      sceneCollectionId,
      Some("Scene Collection"),
      s"Scene collection in layer ${layerStacCollection.id}",
      List[String](),
      "1",
      List[StacProvider](),
      layerStacCollection.extent,
      JsonObject.empty,
      sceneCollectionOwnLinks
    )
  }

  def getLayerStacCollection(
      exportDefinition: StacExport,
      catalog: StacCatalog,
      layerId: UUID,
      sceneTaskAnnotation: ExportData
  ): StacCollection = {

    val layerRootPath = "../catalog.json"
    val layerLinks = List(
      StacLink(
        layerRootPath,
        Parent,
        Some(`application/json`),
        Some(s"Catalog ${catalog.id}"),
        List()
      ),
      StacLink(
        layerRootPath,
        StacRoot,
        Some(`application/json`),
        Some("Root Catalog"),
        List()
      )
    )

    val layerSceneSpatialExtent = {
      val geomExt = sceneTaskAnnotation.scenesGeomExtent
      TwoDimBbox(geomExt.xMin, geomExt.yMin, geomExt.xMax, geomExt.yMax)
    }

    val layerSceneAqcTime: List[Timestamp] =
      sceneTaskAnnotation.scenes map { scene =>
        scene.filterFields.acquisitionDate.getOrElse(scene.createdAt)
      }
    val layerSceneTemporalExtent: TemporalExtent = TemporalExtent(
      layerSceneAqcTime.minBy(_.getTime).toInstant,
      layerSceneAqcTime.maxBy(_.getTime).toInstant
    )

    val layerExtent = StacExtent(
      SpatialExtent(List(layerSceneSpatialExtent)),
      Interval(List(layerSceneTemporalExtent))
    )

    exportDefinition.createStacCollection(
      catalog.stacVersion,
      layerId.toString,
      Some("Layers"),
      "Project Layer Collection",
      List[String](),
      "1.0",
      List[StacProvider](),
      layerExtent,
      JsonObject.empty,
      layerLinks
    )
  }
}
