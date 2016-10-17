package com.azavea.rf.database.query

import java.util.UUID
import java.sql.Timestamp
import java.time.Instant

import geotrellis.slick.Projected
import geotrellis.vector.{Point, Polygon, Extent}

/** Case class representing all /thumbnail query parameters */
case class ThumbnailQueryParameters(
  sceneId: Option[UUID]
)

/** Case class for combined params for images */
case class CombinedImageQueryParams(
  orgParams: OrgQueryParameters,
  timestampParams: TimestampQueryParameters,
  imageParams: ImageQueryParameters
)

/** Query parameters specific to image files */
case class ImageQueryParameters(
  minRawDataBytes: Option[Int],
  maxRawDataBytes: Option[Int],
  scene: Iterable[UUID]
)


/** Case class representing all possible query parameters */
case class SceneQueryParameters(
  maxCloudCover: Option[Float],
  minCloudCover: Option[Float],
  minAcquisitionDatetime: Option[Timestamp],
  maxAcquisitionDatetime: Option[Timestamp],
  datasource: Iterable[String],
  month: Iterable[Int],
  maxSunAzimuth: Option[Float],
  minSunAzimuth: Option[Float],
  maxSunElevation: Option[Float],
  minSunElevation: Option[Float],
  bbox: Option[String],
  point: Option[String]
) {
  val bboxPolygon: Option[Projected[Polygon]] = try {
    bbox
      .map(Extent.fromString)
      .map(_.toPolygon)
      .map(Projected(_, 3857))
  } catch {
    case e: Exception => throw new IllegalArgumentException(
      "Four comma separated coordinates must be given for bbox"
    ).initCause(e)
  }

  val pointGeom: Option[Projected[Point]] = try {
    point.map { s =>
      val Array(x, y) = s.split(",")
      Projected(Point(x.toDouble, y.toDouble), 3857)
    }
  } catch {
    case e: Exception => throw new IllegalArgumentException(
      "Both coordinate parameters of point (x, y) must be specified"
    ).initCause(e)
  }
}

/** Combined all query parameters */
case class CombinedSceneQueryParams(
  orgParams: OrgQueryParameters,
  userParams: UserQueryParameters,
  timestampParams: TimestampQueryParameters,
  sceneParams: SceneQueryParameters
)


/** Case class for bucket query parameters */
case class BucketQueryParameters(
  orgParams: OrgQueryParameters,
  userParams: UserQueryParameters,
  timestampParams: TimestampQueryParameters
)

case class FootprintQueryParameters(
  x: Option[Double],
  y: Option[Double],
  bbox: Option[String]
)

case class CombinedFootprintQueryParams(
  orgParams: OrgQueryParameters,
  timestampParams: TimestampQueryParameters,
  footprintParams: FootprintQueryParameters
)

/** Common query parameters for models that have organization attributes */
case class OrgQueryParameters(
  organizations: Iterable[UUID]
)


/** Query parameters to filter by users */
case class UserQueryParameters(
  createdBy: Option[String],
  modifiedBy: Option[String]
)


/** Query parameters to filter by modified/created times */
case class TimestampQueryParameters(
  minCreateDatetime: Option[Timestamp],
  maxCreateDatetime: Option[Timestamp],
  minModifiedDatetime: Option[Timestamp],
  maxModifiedDatetime: Option[Timestamp]
)
