package com.rasterfoundry.batch.stacExport

import geotrellis.server.stac._
import geotrellis.server.stac.{StacExtent => _}
import geotrellis.vector.reproject.Reproject
import geotrellis.proj4.CRS
import java.util.UUID
import io.circe._
import io.circe.syntax._
import shapeless._
import com.rasterfoundry.datamodel._
import com.rasterfoundry.batch.stacExport.{StacExtent => BatchStacExtent}

object LayerCollectionBuilder {
  sealed trait CollectionRequirements
  object CollectionBuilder {
    trait EmptyCollection extends CollectionRequirements
    trait CollectionStacVersion extends CollectionRequirements
    trait CollectionId extends CollectionRequirements
    trait CollectionTitle extends CollectionRequirements
    trait CollectionExtent extends CollectionRequirements
    trait CollectionLinks extends CollectionRequirements
    trait CollectionDescription extends CollectionRequirements
    trait CollectionParentPath extends CollectionRequirements
    trait CollectionSceneTaskAnnotations extends CollectionRequirements
    type CompleteCollection =
      EmptyCollection
        with CollectionStacVersion
        with CollectionId
        with CollectionTitle
        with CollectionExtent
        with CollectionLinks
        with CollectionDescription
        with CollectionParentPath
        with CollectionSceneTaskAnnotations
  }
}

case class IncompleteLayerCollection(
    stacVersion: Option[String] = None, // required
    id: Option[String] = None, // required
    title: Option[String] = None,
    description: Option[String] = None, // required
    keywords: Option[List[String]] = None,
    version: String = "1", // always 1, we aren't versioning exports
    license: Option[String] = None, // required
    providers: Option[List[StacProvider]] = None,
    extent: Option[BatchStacExtent] = None, // required
    properties: Option[JsonObject] = None,
    links: List[StacLink] = List(), // builders?  // required
    parentPath: Option[String] = None,
    rootPath: Option[String] = None,
    layersToScenes: Map[UUID, List[Scene]] = Map(),
    sceneTaskAnnotations: Option[
      (
          List[Scene],
          Option[UnionedGeomExtent],
          List[Task],
          Option[UnionedGeomExtent],
          Option[Json],
          Option[StacLabelItemPropertiesThin]
      )
    ] = None
) {
  @SuppressWarnings(Array("OptionGet"))
  def toStacCollection(): StacCollection = {
    val extent: Json = this.extent match {
      case Some(ext) => ext.asJson
      case None      => Json.Null
    }
    StacCollection(
      this.stacVersion.get,
      this.id.get,
      this.title,
      this.description.get,
      this.keywords.getOrElse(List()), // not required
      this.version,
      this.license.getOrElse(""), // required but not clear yet
      this.providers.getOrElse(List()), // not required
      extent,
      JsonObject.empty, // properties, free-form json, not required
      this.links
    )
  }
}

class LayerCollectionBuilder[
    CollectionRequirements <: LayerCollectionBuilder.CollectionRequirements
](layerCollection: IncompleteLayerCollection = IncompleteLayerCollection()) {
  import LayerCollectionBuilder.CollectionBuilder._

  def withVersion(
      stacVersion: String
  ): LayerCollectionBuilder[CollectionRequirements with CollectionStacVersion] =
    new LayerCollectionBuilder(
      layerCollection.copy(stacVersion = Some(stacVersion))
    )

  def withId(
      id: String
  ): LayerCollectionBuilder[CollectionRequirements with CollectionId] =
    new LayerCollectionBuilder(layerCollection.copy(id = Some(id)))

  def withTitle(
      title: String
  ): LayerCollectionBuilder[CollectionRequirements with CollectionTitle] =
    new LayerCollectionBuilder(layerCollection.copy(title = Some(title)))

  def withDescription(
      description: String
  ): LayerCollectionBuilder[CollectionRequirements with CollectionDescription] =
    new LayerCollectionBuilder(
      layerCollection.copy(description = Some(description))
    )

  def withLinks(
      links: List[StacLink]
  ): LayerCollectionBuilder[CollectionRequirements with CollectionLinks] =
    new LayerCollectionBuilder(
      layerCollection.copy(links = layerCollection.links ++ links)
    )

  def withExtent(
      extent: BatchStacExtent
  ): LayerCollectionBuilder[CollectionRequirements with CollectionExtent] =
    new LayerCollectionBuilder(layerCollection.copy(extent = Some(extent)))

  def withParentPath(
      parentPath: String,
      rootPath: String
  ): LayerCollectionBuilder[CollectionRequirements with CollectionParentPath] =
    new LayerCollectionBuilder(
      layerCollection
        .copy(parentPath = Some(parentPath), rootPath = Some(rootPath))
    )

  def withSceneTaskAnnotations(
      sceneTaskAnnotations: (
          List[Scene],
          Option[UnionedGeomExtent],
          List[Task],
          Option[UnionedGeomExtent],
          Option[Json],
          Option[StacLabelItemPropertiesThin]
      )
  ): LayerCollectionBuilder[
    CollectionRequirements with CollectionSceneTaskAnnotations
  ] =
    new LayerCollectionBuilder(
      layerCollection.copy(
        sceneTaskAnnotations = Some(sceneTaskAnnotations)
      )
    )

  def inspect: IncompleteLayerCollection = layerCollection

  @SuppressWarnings(Array("OptionGet"))
  def build()(
      implicit ev: CollectionRequirements =:= CompleteCollection
  ): (
      StacCollection, // layer collection
      (StacCollection, List[StacItem]), // scene collection and scene items
      (StacCollection, StacItem, (Option[Json], String)) // label collection, label item, label data, and s3 location
  ) = {
    ev.unused
    // s3://rasterfoundry-production-data-us-east-1/stac-exports/<catalogId>/<layerCollectionId>
    val absPath = layerCollection.parentPath.get
    // ../../<catalogId>
    val rootPath = layerCollection.rootPath.get

    // Build a scene collection and a list of scene items
    val sceneCollectionId = UUID.randomUUID().toString()
    val sceneList = layerCollection.sceneTaskAnnotations.get._1
    val sceneCollectionBuilder = new SceneCollectionBuilder[
      SceneCollectionBuilder.CollectionBuilder.EmptyCollection
    ]()
    // s3://rasterfoundry-production-data-us-east-1/stac-exports/<catalogId>/<layerCollectionId>/<sceneCollectionID>
    val sceneCollectionAbsPath = s"${absPath}/${sceneCollectionId}"
    // ../../../catalog.json
    val sceneCollectionRootPath = s"../${rootPath}"
    val sceneCollectionAbsLink = s"${sceneCollectionAbsPath}/collection.json"
    val sceneCollectionRelLink = s"${sceneCollectionId}/collection.json"
    val sceneCollectionOwnLinks = List(
      StacLink(
        sceneCollectionAbsLink,
        Self,
        Some(`application/json`),
        Some(s"Scene Collection ${sceneCollectionId}"),
        List()
      ),
      StacLink(
        sceneCollectionRootPath,
        StacRoot,
        Some(`application/json`),
        Some("Root"),
        List()
      ),
      StacLink(
        "../collection.json",
        Parent,
        Some(`application/json`),
        Some("Layer Collection"),
        List()
      )
    )
    val (sceneCollection, sceneItems, sceneItemLinks): (
        StacCollection,
        List[StacItem],
        List[(String, String, String)]
    ) = sceneCollectionBuilder
      .withVersion(layerCollection.stacVersion.get)
      .withId(sceneCollectionId)
      .withTitle("Scene collection")
      .withDescription(s"Scene collection in layer ${layerCollection.id.get}")
      .withExtent(layerCollection.extent.get)
      .withSceneList(sceneList)
      .withLinks(sceneCollectionOwnLinks)
      .withParentPath(sceneCollectionAbsPath, sceneCollectionRootPath)
      .build()

    // Build a lable collection and a list of label items
    val labelCollectionId = UUID.randomUUID().toString()
    val labelGeomExtent = layerCollection.sceneTaskAnnotations.get._4
    val labelCollectionBuilder = new LabelCollectionBuilder[
      LabelCollectionBuilder.CollectionBuilder.EmptyCollection
    ]()
    // s3://rasterfoundry-production-data-us-east-1/stac-exports/<catalogId>/<layerCollectionId>/<labelCollectionId>
    val labelCollectionAbsPath = s"${absPath}/${labelCollectionId}"
    // ../../../catalog.json
    val labelCollectionRootPath = s"../${rootPath}"
    val labelCollectionAbsLink = s"${labelCollectionAbsPath}/collection.json"
    val labelCollectionRelLink = s"${labelCollectionId}/collection.json"
    val labelCollectionOwnLinks = List(
      StacLink(
        labelCollectionAbsLink,
        Self,
        Some(`application/json`),
        Some(s"Label Collection ${labelCollectionId}"),
        List()
      ),
      StacLink(
        labelCollectionRootPath,
        StacRoot,
        Some(`application/json`),
        Some("Root"),
        List()
      ),
      StacLink(
        "../collection.json",
        Parent,
        Some(`application/json`),
        Some("Layer Collection"),
        List()
      )
    )
    val tasks = layerCollection.sceneTaskAnnotations.get._3
    val tasksGeomExtent = layerCollection.sceneTaskAnnotations.get._4
    val itemPropsThin = layerCollection.sceneTaskAnnotations.get._6
    val labelSpatialExtent = labelGeomExtent match {
      case Some(extent) =>
        Coproduct[Bbox](
          TwoDimBbox(extent.xMin, extent.yMin, extent.xMax, extent.yMax)
        )

      case None =>
        val ext = tasks
          .map(_.geometry)
          .map(
            geom =>
              Reproject(
                geom.geom,
                CRS.fromEpsgCode(3857),
                CRS.fromEpsgCode(4326)
            )
          )
          .map(_.envelope)
          .reduce((e1, e2) => {
            e1.combine(e2)
          })
        Coproduct[Bbox](TwoDimBbox(ext.xmin, ext.ymin, ext.xmax, ext.ymax))

    }

    val labelExtent: BatchStacExtent = layerCollection.extent.get.copy(
      spatial = SpatialExtent(List(labelSpatialExtent))
    )
    val (labelCollection, labelItem, labelDataS3AbsLink): (
        StacCollection,
        StacItem,
        String
    ) =
      labelCollectionBuilder
        .withVersion(layerCollection.stacVersion.get)
        .withId(labelCollectionId)
        .withTitle("Label collection")
        .withDescription(s"Label collection in layer ${layerCollection.id.get}")
        .withExtent(labelExtent)
        .withLinks(labelCollectionOwnLinks)
        .withParentPath(labelCollectionAbsPath, labelCollectionRootPath)
        .withTasksGeomExtent(tasks, tasksGeomExtent)
        .withItemPropInfo(itemPropsThin.get)
        .withSceneItemLinks(
          sceneItemLinks.map(i => (i._1, s"../${i._2}", i._3))
        ) // adjust relative links
        .build()

    val updatedLayerCollection: StacCollection = layerCollection
      .copy(
        links = layerCollection.links ++ List(
          StacLink(
            labelCollectionRelLink,
            Child,
            Some(`application/json`),
            Some("Label Collection"),
            List()
          ),
          StacLink(
            sceneCollectionRelLink,
            Child,
            Some(`application/json`),
            Some("Scene Collection"),
            List()
          )
        )
      )
      .toStacCollection()
    (
      updatedLayerCollection,
      (sceneCollection, sceneItems),
      (
        labelCollection,
        labelItem,
        (layerCollection.sceneTaskAnnotations.get._5, labelDataS3AbsLink)
      )
    )
  }
}
