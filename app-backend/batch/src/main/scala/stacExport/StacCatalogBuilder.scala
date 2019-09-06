package com.rasterfoundry.batch.stacExport

import geotrellis.server.stac._

import com.rasterfoundry.datamodel._
import geotrellis.proj4.CRS
import geotrellis.vector.reproject.Reproject
import cats.implicits._
import io.circe._
import java.sql.Timestamp

object StacCatalogBuilder {
  sealed trait CatalogRequirements
  object CatalogBuilder {
    trait EmptyCatalog extends CatalogRequirements
    trait CatalogVersion extends CatalogRequirements
    trait CatalogParentPath extends CatalogRequirements
    trait CatalogId extends CatalogRequirements
    trait CatalogTitle extends CatalogRequirements
    trait CatalogDescription extends CatalogRequirements
    trait CatalogLinks extends CatalogRequirements
    trait CatalogContents extends CatalogRequirements
    type CompleteCatalog =
      EmptyCatalog
        with CatalogVersion
        with CatalogParentPath
        with CatalogId
        with CatalogTitle
        with CatalogDescription
        with CatalogLinks
        with CatalogContents
  }
}

case class IncompleteStacCatalog(
    stacVersion: Option[String] = None,
    parentPath: Option[String] = None,
    rootPath: Option[String] = None,
    isRoot: Boolean = false,
    id: Option[String] = None,
    title: Option[String] = None,
    description: Option[String] = None,
    links: List[StacLink] = List(),
    contents: Option[ContentBundle] = None
) {
  @SuppressWarnings(Array("OptionGet"))
  def toStacCatalog(): StacCatalog = {
    StacCatalog(
      stacVersion.get,
      id.get,
      title,
      description.get,
      links
    )
  }
}

class StacCatalogBuilder[
    CatalogRequirements <: StacCatalogBuilder.CatalogRequirements
](stacCatalog: IncompleteStacCatalog = IncompleteStacCatalog()) {
  import StacCatalogBuilder.CatalogBuilder._

  def withVersion(
      stacVersion: String
  ): StacCatalogBuilder[CatalogRequirements with CatalogVersion] =
    new StacCatalogBuilder(stacCatalog.copy(stacVersion = Some(stacVersion)))

  def withParentPath(
      parentPath: String,
      isRoot: Boolean = false,
      rootPath: String = ""
  ): StacCatalogBuilder[CatalogRequirements with CatalogParentPath] =
    new StacCatalogBuilder(
      stacCatalog.copy(
        parentPath = Some(parentPath),
        rootPath = Some(rootPath),
        isRoot = isRoot
      )
    )

  def withId(
      id: String
  ): StacCatalogBuilder[CatalogRequirements with CatalogId] =
    new StacCatalogBuilder(stacCatalog.copy(id = Some(id)))

  def withTitle(
      title: String
  ): StacCatalogBuilder[CatalogRequirements with CatalogTitle] =
    new StacCatalogBuilder(stacCatalog.copy(title = Some(title)))

  def withDescription(
      description: String
  ): StacCatalogBuilder[CatalogRequirements with CatalogDescription] =
    new StacCatalogBuilder(stacCatalog.copy(description = Some(description)))

  def withLinks(
      links: List[StacLink]
  ): StacCatalogBuilder[CatalogRequirements with CatalogLinks] =
    new StacCatalogBuilder(stacCatalog.copy(links = links))

  def withContents(
      contents: ContentBundle
  ): StacCatalogBuilder[CatalogRequirements with CatalogContents] =
    new StacCatalogBuilder(stacCatalog.copy(contents = Some(contents)))

  @SuppressWarnings(Array("OptionGet"))
  def build()(
      implicit ev: CatalogRequirements =:= CompleteCatalog
  ): (
      StacCatalog, // catalog
      List[
        (
            StacCollection, // layer collection
            (StacCollection, List[StacItem]), // scene collection and scene items
            (StacCollection, StacItem, (Option[Json], String)) // label collection, label item, label data, and s3 location
        )
      ]
  ) = {
    // Silence unused warning because scalac warns about phantom types
    ev.unused
    // s3://rasterfoundry-production-data-us-east-1/stac-exports/<catalogId>
    val absPath: String = stacCatalog.parentPath.get
    // catalog.json
    val rootPath = "catalog.json"

    val layerCollectionList: List[
      (
          StacCollection, // layer collection
          (StacCollection, List[StacItem]), // scene collection and scene items
          (StacCollection, StacItem, (Option[Json], String)), // label collection, label item, label data, and s3 location
          String //layerSelfAbsLink
      )
    ] = stacCatalog.contents.get.layerToSceneTaskAnnotation
      .map {
        case (layerId, sceneTaskAnnotation) => (layerId, sceneTaskAnnotation)
      }
      .toList
      .map(layerInfo => {
        val layerId: String = layerInfo._1.toString
        val sceneList: List[Scene] = layerInfo._2._1
        val sceneGeomExtent: Option[UnionedGeomExtent] = layerInfo._2._2
        // s3://rasterfoundry-production-data-us-east-1/stac-exports/<catalogId>/<layerId>
        val layerCollectionAbsPath = s"${absPath}/${layerId}"
        // ../../catalog.json
        val layerRootPath = s"../${rootPath}"
        val layerCollectionBuilder =
          new LayerCollectionBuilder[
            LayerCollectionBuilder.CollectionBuilder.EmptyCollection
          ]()
        val layerSelfAbsLink = s"${layerCollectionAbsPath}/collection.json"
        val layerOwnLinks = List(
          StacLink(
            // s3://rasterfoundry-production-data-us-east-1/stac-exports/<catalogId>/catalog.json
            "../catalog.json",
            Parent,
            Some(`application/json`),
            Some(s"Catalog ${stacCatalog.id.get}")
          ),
          StacLink(
            // s3://rasterfoundry-production-data-us-east-1/stac-exports/<catalogId>/<layerCollectionId>/collection.json
            layerSelfAbsLink,
            Self,
            Some(`application/json`),
            Some(s"Layer Collection ${layerId}")
          ),
          StacLink(
            // s3://rasterfoundry-production-data-us-east-1/stac-exports/<catalogId>/<catalogId>.json
            layerRootPath,
            StacRoot,
            Some(`application/json`),
            Some("Root")
          )
        )
        val layerSceneSpatialExtent: List[Double] = sceneGeomExtent match {
          case Some(geomExt) =>
            List(geomExt.xMin, geomExt.yMin, geomExt.xMax, geomExt.yMax)
          case None =>
            val extent = sceneList
              .map(_.dataFootprint.get)
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
            List(extent.xmin, extent.ymin, extent.xmax, extent.ymax)
        }
        val layerSceneAqcTime: List[Timestamp] =
          sceneList map { scene =>
            scene.filterFields.acquisitionDate.getOrElse(scene.createdAt)
          }
        val layerSceneTemporalExtent: List[Option[String]] = List(
          Some(layerSceneAqcTime.minBy(_.getTime).toLocalDateTime.toString),
          Some(layerSceneAqcTime.maxBy(_.getTime).toLocalDateTime.toString)
        )
        val layerExtent = StacExtent(
          layerSceneSpatialExtent,
          layerSceneTemporalExtent
        )
        val (
          layerCollection,
          (sceneCollection, sceneItems),
          (labelCollection, labelItem, (labelDataJson, labelDataS3AbsLink))
        ): (
            StacCollection,
            (StacCollection, List[StacItem]),
            (StacCollection, StacItem, (Option[Json], String))
        ) = layerCollectionBuilder
          .withVersion(stacCatalog.stacVersion.get)
          .withId(layerId)
          .withTitle("Layers")
          .withDescription("Project layer collection")
          .withLinks(layerOwnLinks)
          .withParentPath(layerCollectionAbsPath, layerRootPath)
          .withExtent(layerExtent)
          .withSceneTaskAnnotations(layerInfo._2)
          .build()
        (
          layerCollection,
          (sceneCollection, sceneItems),
          (labelCollection, labelItem, (labelDataJson, labelDataS3AbsLink)),
          layerSelfAbsLink
        )
      })

    val updatedStacCatalog = stacCatalog
      .copy(
        links = stacCatalog.links ++ layerCollectionList.map {
          case (layerCollection, _, _, _) =>
            StacLink(
              s"${layerCollection.id}/collection.json",
              Child,
              Some(`application/json`),
              Some("Layer Collection")
            )
        }
      )
      .toStacCatalog()
    val layerInfoList = layerCollectionList.map(layerInfo =>
      (layerInfo._1, layerInfo._2, layerInfo._3))

    (updatedStacCatalog, layerInfoList)
  }
}
