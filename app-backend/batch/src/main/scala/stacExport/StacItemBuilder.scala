package com.rasterfoundry.batch.stacExport

import geotrellis.server.stac._
//import java.util.UUID
import io.circe._
import geotrellis.vector.{io => _, _}

case class ItemBbox(
    lowerLeftLng: Double,
    lowerLeftLat: Double,
    upperRightLng: Double,
    upperRightLat: Double
)

object StacItemBuilder {
  sealed trait ItemRequirements
  object ItemBuilder {
    trait EmptyItem extends ItemRequirements
    trait ItemId extends ItemRequirements
    trait ItemGeometries extends ItemRequirements
    trait ItemLinks extends ItemRequirements
    trait ItemCollection extends ItemRequirements
    trait ItemProperties extends ItemRequirements
    trait ItemParentPath extends ItemRequirements
    trait ItemAssets extends ItemRequirements
    type CompleteItem =
      EmptyItem
        with ItemId
        with ItemGeometries
        with ItemLinks
        with ItemCollection
        with ItemProperties
        with ItemParentPath
        with ItemAssets
  }
}

case class IncompleteStacItem(
    id: Option[String] = None,
    _type: String = "feature",
    geometry: Option[Geometry] = None,
    bbox: Option[ItemBbox] = None,
    links: List[StacLink] = List(), // builders? ids? build function of parent needs to provide. relative links
    assets: Map[String, StacAsset] = Map(), // relative links to collection, catalog
    collection: Option[String] = None, // id of collection
    properties: Option[JsonObject] = None,
    parentPath: Option[String] = None,
    rootPath: Option[String] = None,
) {
  @SuppressWarnings(Array("OptionGet"))
  def toStacItem(): StacItem = {
    val itemBbox = this.bbox.get
    StacItem(
      id = this.id.get,
      geometry = this.geometry.get,
      bbox = TwoDimBbox(
        itemBbox.lowerLeftLng,
        itemBbox.lowerLeftLat,
        itemBbox.upperRightLng,
        itemBbox.upperRightLat
      ),
      links = this.links,
      assets = this.assets,
      collection = this.collection,
      properties = this.properties.get
    )
  }
}

class StacItemBuilder[ItemRequirements <: StacItemBuilder.ItemRequirements](
    stacItem: IncompleteStacItem = IncompleteStacItem()) {
  import StacItemBuilder.ItemBuilder._

  def withId(id: String): StacItemBuilder[ItemRequirements with ItemId] =
    new StacItemBuilder(stacItem.copy(id = Some(id)))

  def withGeometries(
      geometry: Geometry,
      bbox: ItemBbox): StacItemBuilder[ItemRequirements with ItemGeometries] =
    new StacItemBuilder(
      stacItem.copy(geometry = Some(geometry), bbox = Some(bbox)))

  def withLinks(
      links: List[StacLink]): StacItemBuilder[ItemRequirements with ItemLinks] =
    new StacItemBuilder(stacItem.copy(links = stacItem.links ++ links))

  def withCollection(collectionId: String)
    : StacItemBuilder[ItemRequirements with ItemCollection] =
    new StacItemBuilder(stacItem.copy(collection = Some(collectionId)))

  def withProperties(properties: JsonObject)
    : StacItemBuilder[ItemRequirements with ItemProperties] =
    new StacItemBuilder(stacItem.copy(properties = Some(properties)))

  def withParentPath(
      path: String,
      rootPath: String): StacItemBuilder[ItemRequirements with ItemParentPath] =
    new StacItemBuilder(
      stacItem.copy(parentPath = Some(path), rootPath = Some(rootPath)))

  def withAssets(assets: Map[String, StacAsset])
    : StacItemBuilder[ItemRequirements with ItemAssets] =
    new StacItemBuilder(stacItem.copy(assets = stacItem.assets ++ assets))

  @SuppressWarnings(Array("OptionGet"))
  def build()(implicit ev: ItemRequirements =:= CompleteItem): StacItem = {
    ev.unused // Silence unused warning because scalac warns about phantom types
    stacItem.toStacItem
  }
}
