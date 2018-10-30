package com.rasterfoundry.datamodel

import java.sql.Timestamp
import java.util.UUID

import io.circe._
import io.circe.generic.semiauto._
import geotrellis.proj4._
import geotrellis.vector.{Extent, Point, Polygon, Projected}

/** Case class representing all /thumbnail query parameters */
final case class ThumbnailQueryParameters(sceneId: Option[UUID] = None)

object ThumbnailQueryParameters {
  implicit def encThumbnailQueryParameters =
    deriveEncoder[ThumbnailQueryParameters]
  implicit def decThumbnailQueryParameters =
    deriveDecoder[ThumbnailQueryParameters]
}

/** Case class for combined params for images */
/** Query parameters specific to image files */
final case class ImageQueryParameters(minRawDataBytes: Option[Long] = None,
                                      maxRawDataBytes: Option[Long] = None,
                                      minResolution: Option[Float] = None,
                                      maxResolution: Option[Float] = None,
                                      scene: Iterable[UUID] = Seq.empty[UUID])

object ImageQueryParameters {
  implicit def encImageQueryParameters = deriveEncoder[ImageQueryParameters]
  implicit def decImageQueryParameters = deriveDecoder[ImageQueryParameters]
}

/** Case class representing all possible query parameters */
@SuppressWarnings(Array("CatchException"))
final case class SceneQueryParameters(
    maxCloudCover: Option[Float] = None,
    minCloudCover: Option[Float] = None,
    minAcquisitionDatetime: Option[Timestamp] = None,
    maxAcquisitionDatetime: Option[Timestamp] = None,
    datasource: Iterable[UUID] = Seq.empty[UUID],
    month: Iterable[Int] = Seq.empty[Int],
    minDayOfMonth: Option[Int] = None,
    maxDayOfMonth: Option[Int] = None,
    maxSunAzimuth: Option[Float] = None,
    minSunAzimuth: Option[Float] = None,
    maxSunElevation: Option[Float] = None,
    minSunElevation: Option[Float] = None,
    bbox: Iterable[String] = Seq.empty[String],
    point: Option[String] = None,
    project: Option[UUID] = None,
    ingested: Option[Boolean] = None,
    ingestStatus: Iterable[String] = Seq.empty[String],
    pending: Option[Boolean] = None,
    shape: Option[UUID] = None
) {
  val bboxPolygon: Option[Seq[Projected[Polygon]]] =
    BboxUtil.toBboxPolygon(bbox)

  val pointGeom: Option[Projected[Point]] = try {
    point.map { s =>
      val Array(x, y) = s.split(",")
      Projected(Point(x.toDouble, y.toDouble), 4326)
        .reproject(LatLng, WebMercator)(3857)
    }
  } catch {
    case e: Exception =>
      throw new IllegalArgumentException(
        "Both coordinate parameters of point (x, y) must be specified"
      ).initCause(e)
  }
}

object SceneQueryParameters {
  implicit def encSceneQueryParameters = deriveEncoder[SceneQueryParameters]
  implicit def decSceneQueryParameters = deriveDecoder[SceneQueryParameters]
}

final case class SceneSearchModeQueryParams(
    exactCount: Option[Boolean] = None
)

object SceneSearchModeQueryParams {
  implicit def encSceneSearchModeQueryParams =
    deriveEncoder[SceneSearchModeQueryParams]
  implicit def decSceneSearchModeQueryParams =
    deriveDecoder[SceneSearchModeQueryParams]
}

/** Combined all query parameters */
final case class CombinedSceneQueryParams(
    orgParams: OrgQueryParameters = OrgQueryParameters(),
    userParams: UserQueryParameters = UserQueryParameters(),
    timestampParams: TimestampQueryParameters = TimestampQueryParameters(),
    sceneParams: SceneQueryParameters = SceneQueryParameters(),
    ownershipTypeParams: OwnershipTypeQueryParameters =
      OwnershipTypeQueryParameters(),
    groupQueryParameters: GroupQueryParameters = GroupQueryParameters(),
    sceneSearchModeParams: SceneSearchModeQueryParams =
      SceneSearchModeQueryParams()
)

object CombinedSceneQueryParams {
  implicit def encCombinedSceneQueryParams =
    deriveEncoder[CombinedSceneQueryParams]
  implicit def decCombinedSceneQueryParams =
    deriveDecoder[CombinedSceneQueryParams]
}

/** Case class for project query parameters */
final case class ProjectQueryParameters(
    orgParams: OrgQueryParameters = OrgQueryParameters(),
    userParams: UserQueryParameters = UserQueryParameters(),
    timestampParams: TimestampQueryParameters = TimestampQueryParameters(),
    searchParams: SearchQueryParameters = SearchQueryParameters(),
    ownershipTypeParams: OwnershipTypeQueryParameters =
      OwnershipTypeQueryParameters(),
    groupQueryParameters: GroupQueryParameters = GroupQueryParameters(),
    tagQueryParameters: TagQueryParameters = TagQueryParameters()
)

object ProjectQueryParameters {
  implicit def encProjectQueryParameters = deriveEncoder[ProjectQueryParameters]
  implicit def decProjectQueryParameters = deriveDecoder[ProjectQueryParameters]
}

final case class AoiQueryParameters(
    orgParams: OrgQueryParameters = OrgQueryParameters(),
    userParams: UserQueryParameters = UserQueryParameters(),
    timestampParams: TimestampQueryParameters = TimestampQueryParameters()
)

object AoiQueryParameters {
  implicit def encAoiQueryParameters = deriveEncoder[AoiQueryParameters]
  implicit def decAoiQueryParameters = deriveDecoder[AoiQueryParameters]
}

/** Combined tool query parameters */
final case class CombinedToolQueryParameters(
    orgParams: OrgQueryParameters = OrgQueryParameters(),
    userParams: UserQueryParameters = UserQueryParameters(),
    timestampParams: TimestampQueryParameters = TimestampQueryParameters(),
    searchParams: SearchQueryParameters = SearchQueryParameters(),
    ownershipTypeParams: OwnershipTypeQueryParameters =
      OwnershipTypeQueryParameters(),
    groupQueryParameters: GroupQueryParameters = GroupQueryParameters()
)

object CombinedToolQueryParameters {
  implicit def encCombinedToolQueryParameters =
    deriveEncoder[CombinedToolQueryParameters]
  implicit def decCombinedToolQueryParameters =
    deriveDecoder[CombinedToolQueryParameters]
}

final case class FootprintQueryParameters(x: Option[Double] = None,
                                          y: Option[Double] = None,
                                          bbox: Option[String] = None)

object FootprintQueryParameters {
  implicit def encFootprintQueryParameters =
    deriveEncoder[FootprintQueryParameters]
  implicit def decFootprintQueryParameters =
    deriveDecoder[FootprintQueryParameters]
}

final case class CombinedFootprintQueryParams(
    orgParams: OrgQueryParameters = OrgQueryParameters(),
    timestampParams: TimestampQueryParameters = TimestampQueryParameters(),
    footprintParams: FootprintQueryParameters = FootprintQueryParameters()
)

object CombinedFootprintQueryParams {
  implicit def encCombinedFootprintQueryParams =
    deriveEncoder[CombinedFootprintQueryParams]
  implicit def decCombinedFootprintQueryParams =
    deriveDecoder[CombinedFootprintQueryParams]
}

/** Common query parameters for models that have organization attributes */
final case class OrgQueryParameters(
    organizations: Iterable[UUID] = Seq.empty[UUID])

object OrgQueryParameters {
  implicit def encOrgQueryParameters = deriveEncoder[OrgQueryParameters]
  implicit def decOrgQueryParameters = deriveDecoder[OrgQueryParameters]
}

/** Query parameters to filter by only users */
final case class UserAuditQueryParameters(createdBy: Option[String] = None,
                                          modifiedBy: Option[String] = None)

object UserAuditQueryParameters {
  implicit def encUserAuditQueryParameters =
    deriveEncoder[UserAuditQueryParameters]
  implicit def decUserAuditQueryParameters =
    deriveDecoder[UserAuditQueryParameters]
}

/** Query parameters to filter by owners */
final case class OwnerQueryParameters(owner: Option[String] = None)

object OwnerQueryParameters {
  implicit def encOwnerQueryParameters = deriveEncoder[OwnerQueryParameters]
  implicit def decOwnerQueryParameters = deriveDecoder[OwnerQueryParameters]
}

/** Query parameters to filter by ownership type:
  *- owned by the requesting user only: owned
  *- shared to the requesting user due to group membership: inherited
  *- shared to the requesting user directly, across platform, or due to group membership: shared
  *- both the above: none, this is default
  */
final case class OwnershipTypeQueryParameters(
    ownershipType: Option[String] = None
)

object OwnershipTypeQueryParameters {
  implicit def encOwnershipTypeQueryParameters =
    deriveEncoder[OwnershipTypeQueryParameters]
  implicit def decOwnershipTypeQueryParameters =
    deriveDecoder[OwnershipTypeQueryParameters]
}

/** Query parameters to filter by group membership*/
final case class GroupQueryParameters(groupType: Option[GroupType] = None,
                                      groupId: Option[UUID] = None)

object GroupQueryParameters {
  implicit def encGroupQueryParameters = deriveEncoder[GroupQueryParameters]
  implicit def decGroupQueryParameters = deriveDecoder[GroupQueryParameters]
}

/** Query parameters to filter by users */
final case class UserQueryParameters(
    onlyUserParams: UserAuditQueryParameters = UserAuditQueryParameters(),
    ownerParams: OwnerQueryParameters = OwnerQueryParameters(),
    activationParams: ActivationQueryParameters = ActivationQueryParameters()
)

object UserQueryParameters {
  implicit def encUserQueryParameters = deriveEncoder[UserQueryParameters]
  implicit def decUserQueryParameters = deriveDecoder[UserQueryParameters]
}

/** Query parameters to filter by modified/created times */
final case class TimestampQueryParameters(
    minCreateDatetime: Option[Timestamp] = None,
    maxCreateDatetime: Option[Timestamp] = None,
    minModifiedDatetime: Option[Timestamp] = None,
    maxModifiedDatetime: Option[Timestamp] = None
)

object TimestampQueryParameters {
  implicit def encTimestampQueryParameters =
    deriveEncoder[TimestampQueryParameters]
  implicit def decTimestampQueryParameters =
    deriveDecoder[TimestampQueryParameters]
}

final case class ToolCategoryQueryParameters(search: Option[String] = None)

object ToolCategoryQueryParameters {
  implicit def encToolCategoryQueryParameters =
    deriveEncoder[ToolCategoryQueryParameters]
  implicit def decToolCategoryQueryParameters =
    deriveDecoder[ToolCategoryQueryParameters]
}

final case class ToolRunQueryParameters(createdBy: Option[String] = None,
                                        projectId: Option[UUID] = None,
                                        toolId: Option[UUID] = None)

object ToolRunQueryParameters {
  implicit def encToolRunQueryParameters = deriveEncoder[ToolRunQueryParameters]
  implicit def decToolRunQueryParameters = deriveDecoder[ToolRunQueryParameters]
}

final case class CombinedToolRunQueryParameters(
    toolRunParams: ToolRunQueryParameters = ToolRunQueryParameters(),
    timestampParams: TimestampQueryParameters = TimestampQueryParameters(),
    ownershipTypeParams: OwnershipTypeQueryParameters =
      OwnershipTypeQueryParameters(),
    groupQueryParameters: GroupQueryParameters = GroupQueryParameters(),
    userParams: UserQueryParameters = UserQueryParameters(),
    searchParams: SearchQueryParameters = SearchQueryParameters()
)

object CombinedToolRunQueryParameters {
  implicit def encCombinedToolRunQueryParameters =
    deriveEncoder[CombinedToolRunQueryParameters]
  implicit def decCombinedToolRunQueryParameters =
    deriveDecoder[CombinedToolRunQueryParameters]
}

final case class CombinedToolCategoryQueryParams(
    timestampParams: TimestampQueryParameters = TimestampQueryParameters(),
    toolCategoryParams: ToolCategoryQueryParameters =
      ToolCategoryQueryParameters()
)

object CombinedToolCategoryQueryParams {
  implicit def encCombinedToolCategoryQueryParams =
    deriveEncoder[CombinedToolCategoryQueryParams]
  implicit def decCombinedToolCategoryQueryParams =
    deriveDecoder[CombinedToolCategoryQueryParams]
}

final case class DatasourceQueryParameters(
    userParams: UserQueryParameters = UserQueryParameters(),
    searchParams: SearchQueryParameters = SearchQueryParameters(),
    ownershipTypeParams: OwnershipTypeQueryParameters =
      OwnershipTypeQueryParameters(),
    groupQueryParameters: GroupQueryParameters = GroupQueryParameters()
)

object DatasourceQueryParameters {
  implicit def encDatasourceQueryParameters =
    deriveEncoder[DatasourceQueryParameters]
  implicit def decDatasourceQueryParameters =
    deriveDecoder[DatasourceQueryParameters]
}

final case class MapTokenQueryParameters(name: Option[String] = None,
                                         projectId: Option[UUID] = None)

object MapTokenQueryParameters {
  implicit def encMapTokenQueryParameters =
    deriveEncoder[MapTokenQueryParameters]
  implicit def decMapTokenQueryParameters =
    deriveDecoder[MapTokenQueryParameters]
}

final case class CombinedMapTokenQueryParameters(
    orgParams: OrgQueryParameters = OrgQueryParameters(),
    userParams: UserQueryParameters = UserQueryParameters(),
    mapTokenParams: MapTokenQueryParameters = MapTokenQueryParameters()
)

object CombinedMapTokenQueryParameters {
  implicit def encCombinedMapTokenQueryParameters =
    deriveEncoder[CombinedMapTokenQueryParameters]
  implicit def decCombinedMapTokenQueryParameters =
    deriveDecoder[CombinedMapTokenQueryParameters]
}

final case class UploadQueryParameters(datasource: Option[UUID] = None,
                                       uploadStatus: Option[String] = None,
                                       projectId: Option[UUID] = None)

object UploadQueryParameters {
  implicit def encUploadQueryParameters = deriveEncoder[UploadQueryParameters]
  implicit def decUploadQueryParameters = deriveDecoder[UploadQueryParameters]
}

final case class ExportQueryParameters(organization: Option[UUID] = None,
                                       project: Option[UUID] = None,
                                       analysis: Option[UUID] = None,
                                       exportStatus: Iterable[String] =
                                         Seq.empty[String])

object ExportQueryParameters {
  implicit def encExportQueryParameters = deriveEncoder[ExportQueryParameters]
  implicit def decExportQueryParameters = deriveDecoder[ExportQueryParameters]
}

final case class DropboxAuthQueryParameters(code: Option[String] = None)

object DropboxAuthQueryParameters {
  implicit def encDropboxAuthQueryParameters =
    deriveEncoder[DropboxAuthQueryParameters]
  implicit def decDropboxAuthQueryParameters =
    deriveDecoder[DropboxAuthQueryParameters]
}

final case class AnnotationQueryParameters(
    orgParams: OrgQueryParameters = OrgQueryParameters(),
    userParams: UserQueryParameters = UserQueryParameters(),
    label: Option[String] = None,
    machineGenerated: Option[Boolean] = None,
    minConfidence: Option[Double] = None,
    maxConfidence: Option[Double] = None,
    quality: Option[String] = None,
    annotationGroup: Option[UUID] = None,
    bbox: Iterable[String] = Seq.empty[String]
) {
  val bboxPolygon: Option[Seq[Projected[Polygon]]] =
    BboxUtil.toBboxPolygon(bbox)
}

object AnnotationQueryParameters {
  implicit def encAnnotationQueryParameters =
    deriveEncoder[AnnotationQueryParameters]
  implicit def decAnnotationQueryParameters =
    deriveDecoder[AnnotationQueryParameters]
}
final case class ShapeQueryParameters(
    orgParams: OrgQueryParameters = OrgQueryParameters(),
    userParams: UserQueryParameters = UserQueryParameters(),
    timestampParams: TimestampQueryParameters = TimestampQueryParameters(),
    ownershipTypeParams: OwnershipTypeQueryParameters =
      OwnershipTypeQueryParameters(),
    groupQueryParameters: GroupQueryParameters = GroupQueryParameters(),
    searchParams: SearchQueryParameters = SearchQueryParameters()
)

object ShapeQueryParameters {
  implicit def encShapeQueryParameters = deriveEncoder[ShapeQueryParameters]
  implicit def decShapeQueryParameters = deriveDecoder[ShapeQueryParameters]
}

final case class FeedQueryParameters(source: Option[String] = None)

object FeedQueryParameters {
  implicit def encFeedQueryParameters = deriveEncoder[FeedQueryParameters]
  implicit def decFeedQueryParameters = deriveDecoder[FeedQueryParameters]
}

final case class SearchQueryParameters(search: Option[String] = None)

object SearchQueryParameters {
  implicit def encSearchQueryParameters = deriveEncoder[SearchQueryParameters]
  implicit def decSearchQueryParameters = deriveDecoder[SearchQueryParameters]
}

final case class ActivationQueryParameters(isActive: Option[Boolean] = None)

object ActivationQueryParameters {
  implicit def encActivationQueryParameters =
    deriveEncoder[ActivationQueryParameters]
  implicit def decActivationQueryParameters =
    deriveDecoder[ActivationQueryParameters]
}

final case class TeamQueryParameters(
    timestampParams: TimestampQueryParameters = TimestampQueryParameters(),
    orgParams: OrgQueryParameters = OrgQueryParameters(),
    onlyUserParams: UserAuditQueryParameters = UserAuditQueryParameters(),
    searchParams: SearchQueryParameters = SearchQueryParameters(),
    activationParams: ActivationQueryParameters = ActivationQueryParameters()
)

object TeamQueryParameters {
  implicit def encTeamQueryParameters = deriveEncoder[TeamQueryParameters]
  implicit def decTeamQueryParameters = deriveDecoder[TeamQueryParameters]
}

final case class PlatformQueryParameters(
    timestampParams: TimestampQueryParameters = TimestampQueryParameters(),
    onlyUserParams: UserAuditQueryParameters = UserAuditQueryParameters(),
    searchParams: SearchQueryParameters = SearchQueryParameters(),
    activationParams: ActivationQueryParameters = ActivationQueryParameters()
)

object PlatformQueryParameters {
  implicit def encPlatformQueryParameters =
    deriveEncoder[PlatformQueryParameters]
  implicit def decPlatformQueryParameters =
    deriveDecoder[PlatformQueryParameters]
}

final case class PlatformIdQueryParameters(platformId: Option[UUID] = None)

object PlatformIdQueryParameters {
  implicit def encPlatformIdQueryParameters =
    deriveEncoder[PlatformIdQueryParameters]
  implicit def decPlatformIdQueryParameters =
    deriveDecoder[PlatformIdQueryParameters]
}

final case class OrganizationQueryParameters(
    timestampParams: TimestampQueryParameters = TimestampQueryParameters(),
    searchParams: SearchQueryParameters = SearchQueryParameters(),
    activationParams: ActivationQueryParameters = ActivationQueryParameters(),
    platformIdParams: PlatformIdQueryParameters = PlatformIdQueryParameters()
)

object OrganizationQueryParameters {
  implicit def encOrganizationQueryParameters =
    deriveEncoder[OrganizationQueryParameters]
  implicit def decOrganizationQueryParameters =
    deriveDecoder[OrganizationQueryParameters]
}

final case class SceneThumbnailQueryParameters(width: Option[Int],
                                               height: Option[Int],
                                               token: String,
                                               red: Option[Int],
                                               green: Option[Int],
                                               blue: Option[Int],
                                               floor: Option[Int])

object SceneThumbnailQueryParameters {
  implicit def encSceneThumbnailQueryParameters =
    deriveEncoder[SceneThumbnailQueryParameters]
  implicit def decSceneThumbnailQueryParameters =
    deriveDecoder[SceneThumbnailQueryParameters]
}

final case class TagQueryParameters(
    tagsInclude: Iterable[String] = Seq.empty[String],
    tagsExclude: Iterable[String] = Seq.empty[String]
)

object TagQueryParameters {
  implicit def encTagQueryParameters = deriveEncoder[TagQueryParameters]
  implicit def decTagQueryParameters = deriveDecoder[TagQueryParameters]
}

object BboxUtil {
  @SuppressWarnings(Array("CatchException"))
  def toBboxPolygon(
      boundingBox: Iterable[String]
  ): Option[Seq[Projected[Polygon]]] =
    try {
      boundingBox match {
        case Nil => None
        case b: Seq[String] =>
          Option[Seq[Projected[Polygon]]](
            b.flatMap(
              _.split(";")
                .map(Extent.fromString)
                .map(_.toPolygon)
                .map(Projected(_, 4326))
                .map(_.reproject(LatLng, WebMercator)(3857))
            )
          )
      }
    } catch {
      case e: Exception =>
        throw new IllegalArgumentException(
          "Four comma separated coordinates must be given for bbox"
        ).initCause(e)
    }
}
