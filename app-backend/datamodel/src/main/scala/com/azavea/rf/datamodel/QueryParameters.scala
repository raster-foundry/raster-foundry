package com.azavea.rf.datamodel

import io.circe.generic.JsonCodec
import geotrellis.proj4._
import geotrellis.slick.Projected
import geotrellis.vector.{Extent, Point, Polygon}

import java.util.UUID
import java.sql.Timestamp
import java.time.Instant


/** Case class representing all /thumbnail query parameters */
@JsonCodec
case class ThumbnailQueryParameters(
  sceneId: Option[UUID] = None
)

/** Case class for combined params for images */
@JsonCodec
case class CombinedImageQueryParams(
  orgParams: OrgQueryParameters = OrgQueryParameters(),
  timestampParams: TimestampQueryParameters = TimestampQueryParameters(),
  imageParams: ImageQueryParameters = ImageQueryParameters()
)

/** Query parameters specific to image files */
@JsonCodec
case class ImageQueryParameters(
  minRawDataBytes: Option[Long] = None,
  maxRawDataBytes: Option[Long] = None,
  minResolution: Option[Float] = None,
  maxResolution: Option[Float] = None,
  scene: Iterable[UUID] = Seq[UUID]()
)

/** Case class representing all possible query parameters */
@JsonCodec
case class SceneQueryParameters(
  maxCloudCover: Option[Float] = None,
  minCloudCover: Option[Float] = None,
  minAcquisitionDatetime: Option[Timestamp] = None,
  maxAcquisitionDatetime: Option[Timestamp] = None,
  datasource: Iterable[UUID] = Seq[UUID](),
  month: Iterable[Int] = Seq[Int](),
  minDayOfMonth: Option[Int] = None,
  maxDayOfMonth: Option[Int] = None,
  maxSunAzimuth: Option[Float] = None,
  minSunAzimuth: Option[Float] = None,
  maxSunElevation: Option[Float] = None,
  minSunElevation: Option[Float] = None,
  bbox: Iterable[String] = Seq[String](),
  point: Option[String] = None,
  project: Option[UUID] = None,
  ingested: Option[Boolean] = None,
  ingestStatus: Iterable[String] = Seq[String](),
  pending: Option[Boolean] = None,
  shape: Option[UUID] = None
) {
  val bboxPolygon: Option[Seq[Projected[Polygon]]] = try {
    bbox match {
      case Nil => None
      case b: Seq[String] => Option[Seq[Projected[Polygon]]](
        b.map(
          _.split(";")
            .map(Extent.fromString)
            .map(_.toPolygon)
            .map(Projected(_, 4326))
            .map(_.reproject(LatLng, WebMercator)(3857))
        ).flatten
      )
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
@JsonCodec
case class GridQueryParameters(
  maxCloudCover: Option[Float] = None,
  minCloudCover: Option[Float] = None,
  minAcquisitionDatetime: Option[Timestamp] = None,
  maxAcquisitionDatetime: Option[Timestamp] = None,
  datasource: Iterable[UUID] = Seq[UUID](),
  month: Iterable[Int] = Seq[Int](),
  minDayOfMonth: Option[Int] = None,
  maxDayOfMonth: Option[Int] = None,
  maxSunAzimuth: Option[Float] = None,
  minSunAzimuth: Option[Float] = None,
  maxSunElevation: Option[Float] = None,
  minSunElevation: Option[Float] = None,
  ingested: Option[Boolean] = None,
  ingestStatus: Iterable[String] = Seq[String]()
)

/** Combined all query parameters */
@JsonCodec
case class CombinedSceneQueryParams(
  orgParams: OrgQueryParameters = OrgQueryParameters(),
  userParams: UserQueryParameters = UserQueryParameters(),
  timestampParams: TimestampQueryParameters = TimestampQueryParameters(),
  sceneParams: SceneQueryParameters = SceneQueryParameters()
)

/** Combined all query parameters for grids */
@JsonCodec
case class CombinedGridQueryParams(
  orgParams: OrgQueryParameters = OrgQueryParameters(),
  userParams: UserQueryParameters = UserQueryParameters(),
  timestampParams: TimestampQueryParameters = TimestampQueryParameters(),
  gridParams: GridQueryParameters = GridQueryParameters(),
  imageParams: ImageQueryParameters = ImageQueryParameters()
)

/** Case class for project query parameters */
@JsonCodec
case class ProjectQueryParameters(
  orgParams: OrgQueryParameters = OrgQueryParameters(),
  userParams: UserQueryParameters = UserQueryParameters(),
  timestampParams: TimestampQueryParameters = TimestampQueryParameters(),
  searchParams: SearchQueryParameters = SearchQueryParameters()
)

@JsonCodec
case class AoiQueryParameters(
  orgParams: OrgQueryParameters = OrgQueryParameters(),
  userParams: UserQueryParameters = UserQueryParameters(),
  timestampParams: TimestampQueryParameters = TimestampQueryParameters()
)

/** Combined tool query parameters */
@JsonCodec
case class CombinedToolQueryParameters(
  orgParams: OrgQueryParameters = OrgQueryParameters(),
  userParams: UserQueryParameters = UserQueryParameters(),
  timestampParams: TimestampQueryParameters = TimestampQueryParameters(),
  searchParams: SearchQueryParameters = SearchQueryParameters()
)

@JsonCodec
case class FootprintQueryParameters(
  x: Option[Double] = None,
  y: Option[Double] = None,
  bbox: Option[String] = None
)

@JsonCodec
case class CombinedFootprintQueryParams(
  orgParams: OrgQueryParameters = OrgQueryParameters(),
  timestampParams: TimestampQueryParameters = TimestampQueryParameters(),
  footprintParams: FootprintQueryParameters = FootprintQueryParameters()
)

/** Common query parameters for models that have organization attributes */
@JsonCodec
case class OrgQueryParameters(
  organizations: Iterable[UUID] = Seq[UUID]()
)

/** Query parameters to filter by only users */
@JsonCodec
case class UserAuditQueryParameters(
  createdBy: Option[String] = None,
  modifiedBy: Option[String] = None
)

/** Query parameters to filter by owners */
@JsonCodec
case class OwnerQueryParameters(
  owner: Option[String] = None
)

/** Query parameters to filter by users */
@JsonCodec
case class UserQueryParameters(
  onlyUserParams: UserAuditQueryParameters = UserAuditQueryParameters(),
  ownerParams: OwnerQueryParameters = OwnerQueryParameters(),
  activationParams: ActivationQueryParameters = ActivationQueryParameters()
)

/** Query parameters to filter by modified/created times */
@JsonCodec
case class TimestampQueryParameters(
  minCreateDatetime: Option[Timestamp] = None,
  maxCreateDatetime: Option[Timestamp] = None,
  minModifiedDatetime: Option[Timestamp] = None,
  maxModifiedDatetime: Option[Timestamp] = None
)

@JsonCodec
case class ToolCategoryQueryParameters(
  search: Option[String] = None
)

@JsonCodec
case class ToolRunQueryParameters(
  createdBy: Option[String] = None,
  projectId: Option[UUID] = None,
  toolId: Option[UUID] = None
)

@JsonCodec
case class CombinedToolRunQueryParameters(
  toolRunParams: ToolRunQueryParameters = ToolRunQueryParameters(),
  timestampParams: TimestampQueryParameters = TimestampQueryParameters()
)

@JsonCodec
case class CombinedToolCategoryQueryParams(
  timestampParams: TimestampQueryParameters = TimestampQueryParameters(),
  toolCategoryParams: ToolCategoryQueryParameters = ToolCategoryQueryParameters()
)

@JsonCodec
case class DatasourceQueryParameters(
  searchParams: SearchQueryParameters = SearchQueryParameters()
)

@JsonCodec
case class MapTokenQueryParameters(
  name: Option[String] = None,
  projectId: Option[UUID] = None
)

@JsonCodec
case class CombinedMapTokenQueryParameters(
  orgParams: OrgQueryParameters = OrgQueryParameters(),
  userParams: UserQueryParameters = UserQueryParameters(),
  mapTokenParams: MapTokenQueryParameters = MapTokenQueryParameters()
)

// TODO add uploadStatus
@JsonCodec
case class UploadQueryParameters(
  organization: Option[UUID] = None,
  datasource: Option[UUID] = None,
  uploadStatus: Option[String] = None
)

@JsonCodec
case class ExportQueryParameters(
  organization: Option[UUID] = None,
  project: Option[UUID] = None,
  exportStatus: Iterable[String] = Seq[String]()
)

@JsonCodec
case class DropboxAuthQueryParameters(
  code: Option[String] = None
)

@JsonCodec
case class AnnotationQueryParameters(
  orgParams: OrgQueryParameters = OrgQueryParameters(),
  userParams: UserQueryParameters = UserQueryParameters(),
  label: Option[String] = None,
  machineGenerated: Option[Boolean] = None,
  minConfidence: Option[Double] = None,
  maxConfidence: Option[Double] = None,
  quality: Option[String] = None
)

@JsonCodec
case class ShapeQueryParameters(
  orgParams: OrgQueryParameters = OrgQueryParameters(),
  userParams: UserQueryParameters = UserQueryParameters(),
  timestampParams: TimestampQueryParameters = TimestampQueryParameters()
)

@JsonCodec
case class FeedQueryParameters(
  source: Option[String] = None
)

@JsonCodec
case class SearchQueryParameters(
  search: Option[String] = None
)

@JsonCodec
case class ActivationQueryParameters(
  isActive: Option[Boolean] = None
)

@JsonCodec
case class TeamQueryParameters(
  timestampParams: TimestampQueryParameters = TimestampQueryParameters(),
  orgParams: OrgQueryParameters = OrgQueryParameters(),
  onlyUserParams: UserAuditQueryParameters = UserAuditQueryParameters(),
  searchParams: SearchQueryParameters = SearchQueryParameters(),
  activationParams: ActivationQueryParameters = ActivationQueryParameters()
)

@JsonCodec
case class PlatformQueryParameters(
  timestampParams: TimestampQueryParameters = TimestampQueryParameters(),
  onlyUserParams: UserAuditQueryParameters = UserAuditQueryParameters(),
  searchParams: SearchQueryParameters = SearchQueryParameters(),
  activationParams: ActivationQueryParameters = ActivationQueryParameters()
)

@JsonCodec
case class PlatformIdQueryParameters(
  platformId: Option[UUID] = None
)

@JsonCodec
case class OrganizationQueryParameters(
  timestampParams: TimestampQueryParameters = TimestampQueryParameters(),
  searchParams: SearchQueryParameters = SearchQueryParameters(),
  activationParams: ActivationQueryParameters = ActivationQueryParameters(),
  platformIdParams: PlatformIdQueryParameters = PlatformIdQueryParameters()
)
