package com.azavea.rf.database.query

import java.util.UUID
import java.sql.Timestamp
import java.time.Instant

import geotrellis.proj4._
import geotrellis.slick.Projected
import geotrellis.vector.{Point, Polygon, Extent}

/** Case class representing all /thumbnail query parameters */
case class ThumbnailQueryParameters(
  sceneId: Option[UUID] = None
)

/** Case class for combined params for images */
case class CombinedImageQueryParams(
  orgParams: OrgQueryParameters = OrgQueryParameters(),
  timestampParams: TimestampQueryParameters = TimestampQueryParameters(),
  imageParams: ImageQueryParameters = ImageQueryParameters()
)

/** Query parameters specific to image files */
case class ImageQueryParameters(
  minRawDataBytes: Option[Int] = None,
  maxRawDataBytes: Option[Int] = None,
  minResolution: Option[Float] = None,
  maxResolution: Option[Float] = None,
  scene: Iterable[UUID] = Seq[UUID]()
)


/** Case class representing all possible query parameters */
case class SceneQueryParameters(
  maxCloudCover: Option[Float] = None,
  minCloudCover: Option[Float] = None,
  minAcquisitionDatetime: Option[Timestamp] = None,
  maxAcquisitionDatetime: Option[Timestamp] = None,
  datasource: Iterable[String] = Seq[String](),
  month: Iterable[Int] = Seq[Int](),
  maxSunAzimuth: Option[Float] = None,
  minSunAzimuth: Option[Float] = None,
  maxSunElevation: Option[Float] = None,
  minSunElevation: Option[Float] = None,
  bbox: Option[String] = None,
  point: Option[String] = None,
  project: Option[UUID] = None
) {
  val bboxPolygon: Option[Seq[Projected[Polygon]]] = try {
    bbox match {
      case Some(b) => Option[Seq[Projected[Polygon]]](
        b.split(";")
        .map(Extent.fromString)
        .map(_.toPolygon)
        .map(Projected(_, 4326))
        .map(_.reproject(LatLng, WebMercator)(3857))
      )
      case _ => None
    }
  } catch {
    case e: Exception => throw new IllegalArgumentException(
      "Four comma separated coordinates must be given for bbox"
    ).initCause(e)
  }

  val pointGeom: Option[Projected[Point]] = try {
    point.map { s =>
      val Array(x, y) = s.split(",")
      Projected(Point(x.toDouble, y.toDouble), 4326).reproject(LatLng, WebMercator)(3857)
    }
  } catch {
    case e: Exception => throw new IllegalArgumentException(
      "Both coordinate parameters of point (x, y) must be specified"
    ).initCause(e)
  }
}

/** Case class for Grid query parameters */
case class GridQueryParameters(
  maxCloudCover: Option[Float] = None,
  minCloudCover: Option[Float] = None,
  minAcquisitionDatetime: Option[Timestamp] = None,
  maxAcquisitionDatetime: Option[Timestamp] = None,
  datasource: Iterable[String] = Seq[String](),
  month: Iterable[Int] = Seq[Int](),
  maxSunAzimuth: Option[Float] = None,
  minSunAzimuth: Option[Float] = None,
  maxSunElevation: Option[Float] = None,
  minSunElevation: Option[Float] = None
)

/** Combined all query parameters */
case class CombinedSceneQueryParams(
  orgParams: OrgQueryParameters = OrgQueryParameters(),
  userParams: UserQueryParameters = UserQueryParameters(),
  timestampParams: TimestampQueryParameters = TimestampQueryParameters(),
  sceneParams: SceneQueryParameters = SceneQueryParameters(),
  imageQueryParameters: ImageQueryParameters = ImageQueryParameters()
)

/** Combined all query parameters for grids */
case class CombinedGridQueryParams(
  orgParams: OrgQueryParameters = OrgQueryParameters(),
  userParams: UserQueryParameters = UserQueryParameters(),
  timestampParams: TimestampQueryParameters = TimestampQueryParameters(),
  gridParams: GridQueryParameters = GridQueryParameters(),
  imageParams: ImageQueryParameters = ImageQueryParameters()
)


/** Case class for project query parameters */
case class ProjectQueryParameters(
  orgParams: OrgQueryParameters = OrgQueryParameters(),
  userParams: UserQueryParameters = UserQueryParameters(),
  timestampParams: TimestampQueryParameters = TimestampQueryParameters()
)

/** Query parameters specific to tools */
case class ToolQueryParameters(
  minRating: Option[Double] = None,
  maxRating: Option[Double] = None,
  toolCategory: Iterable[String] = Seq[String](),
  toolTag: Iterable[String] = Seq[String](),
  search: Option[String] = None
)

/** Combined tool query parameters */
case class CombinedToolQueryParameters(
  orgParams: OrgQueryParameters = OrgQueryParameters(),
  userParams: UserQueryParameters = UserQueryParameters(),
  timestampParams: TimestampQueryParameters = TimestampQueryParameters(),
  toolParams: ToolQueryParameters = ToolQueryParameters()
)

case class FootprintQueryParameters(
  x: Option[Double] = None,
  y: Option[Double] = None,
  bbox: Option[String] = None
)

case class CombinedFootprintQueryParams(
  orgParams: OrgQueryParameters = OrgQueryParameters(),
  timestampParams: TimestampQueryParameters = TimestampQueryParameters(),
  footprintParams: FootprintQueryParameters = FootprintQueryParameters()
)

/** Common query parameters for models that have organization attributes */
case class OrgQueryParameters(
  organizations: Iterable[UUID] = Seq[UUID]()
)


/** Query parameters to filter by users */
case class UserQueryParameters(
  createdBy: Option[String] = None,
  modifiedBy: Option[String] = None
)


/** Query parameters to filter by modified/created times */
case class TimestampQueryParameters(
  minCreateDatetime: Option[Timestamp] = None,
  maxCreateDatetime: Option[Timestamp] = None,
  minModifiedDatetime: Option[Timestamp] = None,
  maxModifiedDatetime: Option[Timestamp] = None
)

case class ToolCategoryQueryParameters(
  search: Option[String] = None
)

case class CombinedToolCategoryQueryParams(
  timestampParams: TimestampQueryParameters = TimestampQueryParameters(),
  toolCategoryParams: ToolCategoryQueryParameters = ToolCategoryQueryParameters()
)
