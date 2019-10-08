package com.rasterfoundry.batch.stacExport

import geotrellis.server.stac._
import io.circe._
import com.rasterfoundry.datamodel._
import geotrellis.server.stac.{StacExtent => _}
import com.rasterfoundry.batch.stacExport.{StacExtent => BatchStacExtent}
import io.circe._
import io.circe.syntax._
import io.circe.syntax._
import java.util.UUID
import java.sql.Timestamp
import java.time.Instant

object LabelCollectionBuilder {
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
    trait CollectionTasksGeomExtent extends CollectionRequirements
    trait CollectionItemInfoThin extends CollectionRequirements
    trait CollectionSceneItemLinks extends CollectionRequirements
    type CompleteCollection =
      EmptyCollection
        with CollectionStacVersion
        with CollectionId
        with CollectionTitle
        with CollectionExtent
        with CollectionLinks
        with CollectionDescription
        with CollectionParentPath
        with CollectionTasksGeomExtent
        with CollectionItemInfoThin
        with CollectionSceneItemLinks
  }
}

case class IncompleteLabelCollection(
    stacVersion: Option[String] = None,
    id: Option[String] = None,
    title: Option[String] = None,
    description: Option[String] = None,
    keywords: Option[List[String]] = None,
    version: String = "1", // always 1, we aren't versioning exports
    license: Option[String] = None,
    providers: List[StacProvider] = List(),
    extent: Option[BatchStacExtent] = None,
    properties: Option[JsonObject] = None,
    links: List[StacLink] = List(), // builders?
    parentPath: Option[String] = None,
    rootPath: Option[String] = None,
    tasks: List[Task] = List(),
    tasksGeomExtent: Option[UnionedGeomExtent] = None,
    itemPropsThin: StacLabelItemPropertiesThin = StacLabelItemPropertiesThin(),
    sceneItemLinks: List[(String, String, String)] = List()
) {
  // it is ok to use .get in here because stacVersion, id,
  // description are in the requirement above and only
  // when they are populated does the compiler agree with
  // the .build() call
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
      this.providers, // not required
      extent,
      JsonObject.empty, // properties, free-form json, not required
      this.links
    )
  }
}

class LabelCollectionBuilder[
    CollectionRequirements <: LabelCollectionBuilder.CollectionRequirements
](labelCollection: IncompleteLabelCollection = IncompleteLabelCollection()) {
  import LabelCollectionBuilder.CollectionBuilder._

  def withVersion(
      version: String
  ): LabelCollectionBuilder[CollectionRequirements with CollectionStacVersion] =
    new LabelCollectionBuilder(
      labelCollection.copy(stacVersion = Some(version))
    )

  def withId(
      id: String
  ): LabelCollectionBuilder[CollectionRequirements with CollectionId] =
    new LabelCollectionBuilder(labelCollection.copy(id = Some(id)))

  def withTitle(
      title: String
  ): LabelCollectionBuilder[CollectionRequirements with CollectionTitle] =
    new LabelCollectionBuilder(labelCollection.copy(title = Some(title)))

  def withDescription(
      description: String
  ): LabelCollectionBuilder[CollectionRequirements with CollectionDescription] =
    new LabelCollectionBuilder(
      labelCollection.copy(description = Some(description))
    )

  def withLinks(
      links: List[StacLink]
  ): LabelCollectionBuilder[CollectionRequirements with CollectionLinks] =
    new LabelCollectionBuilder(
      labelCollection.copy(links = labelCollection.links ++ links)
    )

  def withExtent(
      extent: StacExtent
  ): LabelCollectionBuilder[CollectionRequirements with CollectionExtent] =
    new LabelCollectionBuilder(labelCollection.copy(extent = Some(extent)))

  def withParentPath(
      parentPath: String,
      rootPath: String
  ): LabelCollectionBuilder[CollectionRequirements with CollectionParentPath] =
    new LabelCollectionBuilder(
      labelCollection
        .copy(parentPath = Some(parentPath), rootPath = Some(rootPath))
    )

  def withTasksGeomExtent(
      tasks: List[Task],
      tasksGeomExtent: Option[UnionedGeomExtent]
  ): LabelCollectionBuilder[
    CollectionRequirements with CollectionTasksGeomExtent
  ] =
    new LabelCollectionBuilder(
      labelCollection.copy(
        tasks = labelCollection.tasks ++ tasks,
        tasksGeomExtent = tasksGeomExtent
      )
    )

  def withItemPropInfo(
      itemPropsThin: StacLabelItemPropertiesThin
  ): LabelCollectionBuilder[
    CollectionRequirements with CollectionItemInfoThin
  ] =
    new LabelCollectionBuilder(
      labelCollection.copy(itemPropsThin = itemPropsThin)
    )

  def withSceneItemLinks(
      sceneItemLinks: List[(String, String, String)]
  ): LabelCollectionBuilder[
    CollectionRequirements with CollectionSceneItemLinks
  ] =
    new LabelCollectionBuilder(
      labelCollection.copy(
        sceneItemLinks = labelCollection.sceneItemLinks ++ sceneItemLinks
      )
    )
  // it is ok to use .get in here because paths, tasksGeomExtent,
  // extent, and id are in the requirement above and only
  // when they are populated does the compiler agree with
  // the .build() call
  @SuppressWarnings(Array("OptionGet"))
  def build()(
      implicit ev: CollectionRequirements =:= CompleteCollection
  ): (StacCollection, StacItem, String) = {
    // Silence unused warning because scalac warns about phantom types
    ev.unused
    // s3://rasterfoundry-production-data-us-east-1/stac-exports/<catalogId>/<layerCollectionId>/<labelCollectionId>
    val absPath = labelCollection.parentPath.get
    // ../../../catalog.json
    val rootPath = labelCollection.rootPath.get

    val itemBuilder =
      new StacItemBuilder[StacItemBuilder.ItemBuilder.EmptyItem]()
    val labelItemId = UUID.randomUUID().toString()
    val labelItemGeomExtent = labelCollection.tasksGeomExtent.get
    val labelItemFootprint = labelItemGeomExtent.geometry
    val labelItemBbox = ItemBbox(
      labelItemGeomExtent.xMin,
      labelItemGeomExtent.yMin,
      labelItemGeomExtent.xMax,
      labelItemGeomExtent.yMax
    )

    // s3://rasterfoundry-production-data-us-east-1/stac-exports/<catalogId>/<layerCollectionId>/<labelCollectionId>/<labelItemId>
    val labelItemSelfAbsPath = s"${absPath}/${labelItemId}"
    // s3://rasterfoundry-production-data-us-east-1/stac-exports/<catalogId>/<layerCollectionId>/<labelCollectionId>/<labelItemId>/item.json
    val labelItemSelfAbsLink = s"${labelItemSelfAbsPath}/item.json"
    val labelItemSelfRelLink = s"${labelItemId}/item.json"
    val labelItemLinks = List(
      StacLink(
        labelItemSelfAbsLink,
        Self,
        Some(`application/json`),
        Some(s"Label item ${labelItemId}"),
        List(labelItemId)
      ),
      StacLink(
        "../collection.json",
        Parent,
        Some(`application/json`),
        Some("Label Collection"),
        List()
      ),
      StacLink(
        s"../${rootPath}",
        StacRoot,
        Some(`application/json`),
        Some("Root"),
        List()
      )
    ) ++ labelCollection.sceneItemLinks.map(link => {
      StacLink(
        s"../${link._2}",
        Source,
        Some(`image/cog`),
        Some("Source image STAC item for the label item"),
        List()
      )
    })
    val dateTime
      : List[Timestamp] = labelCollection.extent.get.temporal.interval.flatten map {
      case Some(dt) => Timestamp.from(Instant.parse(s"${dt}Z"))
      case _        => new Timestamp(new java.util.Date().getTime)
    }
    val labelItemProperties = StacLabelItemProperties(
      labelCollection.itemPropsThin.property,
      labelCollection.itemPropsThin.classes,
      "Labels in layer",
      labelCollection.itemPropsThin._type,
      Some(List(labelCollection.itemPropsThin.task)),
      Some(List("manual")),
      None,
      dateTime(0)
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
    val labelDataS3AbsLink: String = s"${labelItemSelfAbsPath}/data.geojson"
    val labelAsset = Map(
      labelItemId ->
        StacAsset(
          labelDataRelLink,
          Some("Label Data Feature Collection"),
          Some(`application/geo+json`)
        )
    )
    val stacExtensions = List("label")
    val labelItem: StacItem = itemBuilder
      .withId(labelItemId)
      .withGeometries(labelItemFootprint, labelItemBbox)
      .withLinks(labelItemLinks)
      .withCollection(labelCollection.id.get)
      .withProperties(labelItemPropertiesJsonObj)
      .withParentPath(absPath, rootPath)
      .withAssets(labelAsset)
      .withStacVersion(labelCollection.stacVersion)
      .withExtensions(stacExtensions)
      .build()

    (
      labelCollection
        .copy(
          links = labelCollection.links ++ List(
            StacLink(
              labelItemSelfRelLink,
              Item,
              Some(`application/json`),
              Some("STAC label item link"),
              List()
            )
          )
        )
        .toStacCollection(),
      labelItem,
      labelDataS3AbsLink
    )
  }
}
