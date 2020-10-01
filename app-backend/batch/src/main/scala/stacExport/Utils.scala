package com.rasterfoundry.batch.stacExport

import com.rasterfoundry.datamodel._

import cats.implicits._
import com.azavea.stac4s._
import geotrellis.proj4.CRS
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
    StacLinkType.StacRoot,
    Some(`application/json`),
    Some("Root")
  )

  private val relativeLayerCollection = StacLink(
    "../collection.json",
    StacLinkType.Parent,
    Some(`application/json`),
    Some("Layer Collection")
  )

  def getAnnotationProjectStacCatalog(
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
        StacLinkType.StacRoot,
        Some(`application/json`),
        Some(s"Catalog $catalogId")
      )
    ) ++ layerIds.map { layerId =>
      StacLink(
        "./layer-collection/collection.json",
        StacLinkType.Child,
        Some(`application/json`),
        Some(s"Layer Collection $layerId")
      )
    }
    // below should be scope-specific
    // since exports implement "label" extension on STAC Item level
    // this field should be an empty list on catalog
    val stacExtensions = List()
    StacCatalog(
      stacVersion,
      stacExtensions, // list of exten
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
        StacLinkType.Parent,
        Some(`application/json`),
        Some("Label Collection")
      ),
      StacLink(
        "./collection.json",
        StacLinkType.Collection,
        Some(`application/json`),
        Some("Label Collection")
      ),
      relativeCatalogRoot
    ) ++ sceneItems.map(item => {
      val stacItemURI = new URI(item.absolutePath)
      val relativeStacItemPath =
        s"../${catalogRootPath.relativize(stacItemURI)}"
      // specify which scene assets these labels are created on
      val labelAssets = JsonObject.fromMap(
        Map(
          "label:assets" -> List(item.item.id).asJson
        )
      )
      StacLink(
        relativeStacItemPath,
        StacLinkType.Source,
        Some(`application/json`),
        Some("Source image STAC item for this label item"),
        labelAssets
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

    val labelItemPropertiesJsonObj = JsonObject.fromMap(
      Map(
        "label:properties" -> sceneTaskAnnotation.labelItemExtension.properties.asJson,
        "label:classes" -> sceneTaskAnnotation.labelItemExtension.classes.asJson,
        "label:description" -> sceneTaskAnnotation.labelItemExtension.description.asJson,
        "label:type" -> sceneTaskAnnotation.labelItemExtension._type.asJson,
        "label:tasks" -> sceneTaskAnnotation.labelItemExtension.tasks.asJson,
        "label:methods" -> sceneTaskAnnotation.labelItemExtension.methods.asJson,
        "label:overviews" -> sceneTaskAnnotation.labelItemExtension.overviews.asJson,
        "datetime" -> dateTime.asJson
      )
    )
    val labelDataRelLink = "./data.geojson"
    val labelAsset = Map(
      "label" ->
        StacItemAsset(
          labelDataRelLink,
          Some("Label Data Feature Collection"),
          Some("Label Data Feature Collection"),
          Set(StacAssetRole.Data),
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
      imageCollection: StacCollection,
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
        StacLinkType.Parent,
        Some(`application/json`),
        Some("Images Collection")
      ),
      StacLink(
        "./collection.json",
        StacLinkType.Collection,
        Some(`application/json`),
        Some("Images Collection")
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
          StacItemAsset(
            s"./${scene.id}.tiff",
            Some("scene"),
            Some("scene"),
            Set(StacAssetRole.Data),
            Some(`image/cog`)
          )
      )
    }
    val sceneItemAbsolutePath =
      s"$layerCollectionAbsolutePath/images/${scene.id}.json"

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
            Some(imageCollection.id),
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
      List(),
      UUID.randomUUID().toString,
      Some("Label Collection"),
      s"Label Collection in layer ${layerStacCollection.id}",
      List[String](),
      List[StacProvider](),
      layerStacCollection.extent,
      JsonObject.empty,
      JsonObject.empty,
      labelCollectionLinks
    )
  }

  def getImagesCollection(
      exportDefinition: StacExport,
      catalog: StacCatalog,
      layerStacCollection: StacCollection
  ): StacCollection = {
    val imageCollectionId = UUID.randomUUID().toString
    val imageCollectionOwnLinks = List(
      relativeCatalogRoot,
      relativeLayerCollection
    )
    exportDefinition.createStacCollection(
      catalog.stacVersion,
      List(),
      imageCollectionId,
      Some("Images Collection"),
      s"Images collection in layer ${layerStacCollection.id}",
      List[String](),
      List[StacProvider](),
      layerStacCollection.extent,
      JsonObject.empty,
      JsonObject.empty,
      imageCollectionOwnLinks
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
        StacLinkType.Parent,
        Some(`application/json`),
        Some(s"Catalog ${catalog.id}")
      ),
      StacLink(
        layerRootPath,
        StacLinkType.StacRoot,
        Some(`application/json`),
        Some("Root Catalog")
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
      List(),
      layerId.toString,
      Some("Layers"),
      "Project Layer Collection",
      List[String](),
      List[StacProvider](),
      layerExtent,
      JsonObject.empty,
      JsonObject.empty,
      layerLinks
    )
  }
}
