package com.rasterfoundry.datamodel

import geotrellis.proj4.{io => _, _}
import geotrellis.vector.{Extent, Point, Polygon, Projected}
import io.circe._
import io.circe.generic.semiauto._

import java.sql.Timestamp
import java.util.UUID

/** Case class representing all /thumbnail query parameters */
final case class ThumbnailQueryParameters(sceneId: Option[UUID] = None)

object ThumbnailQueryParameters {
  implicit def encThumbnailQueryParameters: Encoder[ThumbnailQueryParameters] =
    deriveEncoder[ThumbnailQueryParameters]
  implicit def decThumbnailQueryParameters: Decoder[ThumbnailQueryParameters] =
    deriveDecoder[ThumbnailQueryParameters]
}

/** Case class for combined params for images */
/** Query parameters specific to image files */
final case class ImageQueryParameters(
    minRawDataBytes: Option[Long] = None,
    maxRawDataBytes: Option[Long] = None,
    minResolution: Option[Float] = None,
    maxResolution: Option[Float] = None,
    scene: Iterable[UUID] = Seq.empty[UUID]
)

object ImageQueryParameters {
  implicit def encImageQueryParameters: Encoder[ImageQueryParameters] =
    deriveEncoder[ImageQueryParameters]
  implicit def decImageQueryParameters: Decoder[ImageQueryParameters] =
    deriveDecoder[ImageQueryParameters]
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
    layer: Option[UUID] = None,
    ingested: Option[Boolean] = None,
    ingestStatus: Iterable[String] = Seq.empty[String],
    pending: Option[Boolean] = None,
    shape: Option[UUID] = None,
    projectLayerShape: Option[UUID] = None
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
  implicit def encSceneQueryParameters: Encoder[SceneQueryParameters] =
    deriveEncoder[SceneQueryParameters]
  implicit def decSceneQueryParameters: Decoder[SceneQueryParameters] =
    deriveDecoder[SceneQueryParameters]
}

final case class SceneSearchModeQueryParams(
    exactCount: Option[Boolean] = None
)

object SceneSearchModeQueryParams {
  implicit def encSceneSearchModeQueryParams
    : Encoder[SceneSearchModeQueryParams] =
    deriveEncoder[SceneSearchModeQueryParams]
  implicit def decSceneSearchModeQueryParams
    : Decoder[SceneSearchModeQueryParams] =
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
  implicit def encCombinedSceneQueryParams: Encoder[CombinedSceneQueryParams] =
    deriveEncoder[CombinedSceneQueryParams]
  implicit def decCombinedSceneQueryParams: Decoder[CombinedSceneQueryParams] =
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
    tagQueryParameters: TagQueryParameters = TagQueryParameters(),
    analysisId: Option[UUID] = None
)

object ProjectQueryParameters {
  implicit def encProjectQueryParameters: Encoder[ProjectQueryParameters] =
    deriveEncoder[ProjectQueryParameters]
  implicit def decProjectQueryParameters: Decoder[ProjectQueryParameters] =
    deriveDecoder[ProjectQueryParameters]
}

final case class ProjectSceneQueryParameters(
    ingested: Option[Boolean] = None,
    ingestStatus: Iterable[String] = Seq.empty[String],
    accepted: Option[Boolean] = Some(true)
)

object ProjectSceneQueryParameters {
  implicit def encProjectSceneQueryParameters
    : Encoder[ProjectSceneQueryParameters] =
    deriveEncoder[ProjectSceneQueryParameters]
  implicit def decProjectQueryParameters: Decoder[ProjectSceneQueryParameters] =
    deriveDecoder[ProjectSceneQueryParameters]
}

final case class ToolQueryParameters(
    singleSource: Option[Boolean] = None
)

object ToolQueryParameters {
  implicit def encToolQueryParameters: Encoder[ToolQueryParameters] =
    deriveEncoder[ToolQueryParameters]
  implicit def decToolQueryParameters: Decoder[ToolQueryParameters] =
    deriveDecoder[ToolQueryParameters]
}

/** Combined tool query parameters */
final case class CombinedToolQueryParameters(
    toolParams: ToolQueryParameters = ToolQueryParameters(),
    orgParams: OrgQueryParameters = OrgQueryParameters(),
    userParams: UserQueryParameters = UserQueryParameters(),
    timestampParams: TimestampQueryParameters = TimestampQueryParameters(),
    searchParams: SearchQueryParameters = SearchQueryParameters(),
    ownerQueryParams: OwnerQueryParameters = OwnerQueryParameters(),
    ownershipTypeParams: OwnershipTypeQueryParameters =
      OwnershipTypeQueryParameters(),
    groupQueryParameters: GroupQueryParameters = GroupQueryParameters()
)

object CombinedToolQueryParameters {
  implicit def encCombinedToolQueryParameters
    : Encoder[CombinedToolQueryParameters] =
    deriveEncoder[CombinedToolQueryParameters]
  implicit def decCombinedToolQueryParameters
    : Decoder[CombinedToolQueryParameters] =
    deriveDecoder[CombinedToolQueryParameters]
}

final case class FootprintQueryParameters(
    x: Option[Double] = None,
    y: Option[Double] = None,
    bbox: Option[String] = None
)

object FootprintQueryParameters {
  implicit def encFootprintQueryParameters: Encoder[FootprintQueryParameters] =
    deriveEncoder[FootprintQueryParameters]
  implicit def decFootprintQueryParameters: Decoder[FootprintQueryParameters] =
    deriveDecoder[FootprintQueryParameters]
}

final case class CombinedFootprintQueryParams(
    orgParams: OrgQueryParameters = OrgQueryParameters(),
    timestampParams: TimestampQueryParameters = TimestampQueryParameters(),
    footprintParams: FootprintQueryParameters = FootprintQueryParameters()
)

object CombinedFootprintQueryParams {
  implicit def encCombinedFootprintQueryParams
    : Encoder[CombinedFootprintQueryParams] =
    deriveEncoder[CombinedFootprintQueryParams]
  implicit def decCombinedFootprintQueryParams
    : Decoder[CombinedFootprintQueryParams] =
    deriveDecoder[CombinedFootprintQueryParams]
}

/** Common query parameters for models that have organization attributes */
final case class OrgQueryParameters(
    organizations: Iterable[UUID] = Seq.empty[UUID]
)

object OrgQueryParameters {
  implicit def encOrgQueryParameters: Encoder[OrgQueryParameters] =
    deriveEncoder[OrgQueryParameters]
  implicit def decOrgQueryParameters: Decoder[OrgQueryParameters] =
    deriveDecoder[OrgQueryParameters]
}

/** Query parameters to filter by only users */
final case class UserAuditQueryParameters(
    createdBy: Option[String] = None
)

object UserAuditQueryParameters {
  implicit def encUserAuditQueryParameters: Encoder[UserAuditQueryParameters] =
    deriveEncoder[UserAuditQueryParameters]
  implicit def decUserAuditQueryParameters: Decoder[UserAuditQueryParameters] =
    deriveDecoder[UserAuditQueryParameters]
}

/** Query parameters to filter by owners */
final case class OwnerQueryParameters(
    owner: Iterable[String] = List.empty[String]
)

object OwnerQueryParameters {
  implicit def encOwnerQueryParameters: Encoder[OwnerQueryParameters] =
    deriveEncoder[OwnerQueryParameters]
  implicit def decOwnerQueryParameters: Decoder[OwnerQueryParameters] =
    deriveDecoder[OwnerQueryParameters]
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
  implicit def encOwnershipTypeQueryParameters
    : Encoder[OwnershipTypeQueryParameters] =
    deriveEncoder[OwnershipTypeQueryParameters]
  implicit def decOwnershipTypeQueryParameters
    : Decoder[OwnershipTypeQueryParameters] =
    deriveDecoder[OwnershipTypeQueryParameters]
}

/** Query parameters to filter by group membership*/
final case class GroupQueryParameters(
    groupType: Option[GroupType] = None,
    groupId: Option[UUID] = None
)

object GroupQueryParameters {
  implicit def encGroupQueryParameters: Encoder[GroupQueryParameters] =
    deriveEncoder[GroupQueryParameters]
  implicit def decGroupQueryParameters: Decoder[GroupQueryParameters] =
    deriveDecoder[GroupQueryParameters]
}

/** Query parameters to filter by users */
final case class UserQueryParameters(
    onlyUserParams: UserAuditQueryParameters = UserAuditQueryParameters(),
    ownerParams: OwnerQueryParameters = OwnerQueryParameters(),
    activationParams: ActivationQueryParameters = ActivationQueryParameters()
)

object UserQueryParameters {
  implicit def encUserQueryParameters: Encoder[UserQueryParameters] =
    deriveEncoder[UserQueryParameters]
  implicit def decUserQueryParameters: Decoder[UserQueryParameters] =
    deriveDecoder[UserQueryParameters]
}

/** Query parameters to filter by modified/created times */
final case class TimestampQueryParameters(
    minCreateDatetime: Option[Timestamp] = None,
    maxCreateDatetime: Option[Timestamp] = None,
    minModifiedDatetime: Option[Timestamp] = None,
    maxModifiedDatetime: Option[Timestamp] = None
)

object TimestampQueryParameters {
  implicit def encTimestampQueryParameters: Encoder[TimestampQueryParameters] =
    deriveEncoder[TimestampQueryParameters]
  implicit def decTimestampQueryParameters: Decoder[TimestampQueryParameters] =
    deriveDecoder[TimestampQueryParameters]
}

final case class ToolRunQueryParameters(
    createdBy: Option[String] = None,
    projectId: Option[UUID] = None,
    templateId: Option[UUID] = None,
    projectLayerId: Option[UUID] = None
)

object ToolRunQueryParameters {
  implicit def encToolRunQueryParameters: Encoder[ToolRunQueryParameters] =
    deriveEncoder[ToolRunQueryParameters]
  implicit def decToolRunQueryParameters: Decoder[ToolRunQueryParameters] =
    deriveDecoder[ToolRunQueryParameters]
}

final case class CombinedToolRunQueryParameters(
    toolRunParams: ToolRunQueryParameters = ToolRunQueryParameters(),
    timestampParams: TimestampQueryParameters = TimestampQueryParameters(),
    ownerParams: OwnerQueryParameters = OwnerQueryParameters(),
    ownershipTypeParams: OwnershipTypeQueryParameters =
      OwnershipTypeQueryParameters(),
    groupQueryParameters: GroupQueryParameters = GroupQueryParameters(),
    userParams: UserQueryParameters = UserQueryParameters(),
    searchParams: SearchQueryParameters = SearchQueryParameters()
)

object CombinedToolRunQueryParameters {
  implicit def encCombinedToolRunQueryParameters
    : Encoder[CombinedToolRunQueryParameters] =
    deriveEncoder[CombinedToolRunQueryParameters]
  implicit def decCombinedToolRunQueryParameters
    : Decoder[CombinedToolRunQueryParameters] =
    deriveDecoder[CombinedToolRunQueryParameters]
}

final case class DatasourceQueryParameters(
    userParams: UserQueryParameters = UserQueryParameters(),
    searchParams: SearchQueryParameters = SearchQueryParameters(),
    ownershipTypeParams: OwnershipTypeQueryParameters =
      OwnershipTypeQueryParameters(),
    groupQueryParameters: GroupQueryParameters = GroupQueryParameters()
)

object DatasourceQueryParameters {
  implicit def encDatasourceQueryParameters
    : Encoder[DatasourceQueryParameters] =
    deriveEncoder[DatasourceQueryParameters]
  implicit def decDatasourceQueryParameters
    : Decoder[DatasourceQueryParameters] =
    deriveDecoder[DatasourceQueryParameters]
}

final case class MapTokenQueryParameters(
    name: Option[String] = None,
    projectId: Option[UUID] = None
)

object MapTokenQueryParameters {
  implicit def encMapTokenQueryParameters: Encoder[MapTokenQueryParameters] =
    deriveEncoder[MapTokenQueryParameters]
  implicit def decMapTokenQueryParameters: Decoder[MapTokenQueryParameters] =
    deriveDecoder[MapTokenQueryParameters]
}

final case class CombinedMapTokenQueryParameters(
    orgParams: OrgQueryParameters = OrgQueryParameters(),
    userParams: UserQueryParameters = UserQueryParameters(),
    mapTokenParams: MapTokenQueryParameters = MapTokenQueryParameters()
)

object CombinedMapTokenQueryParameters {
  implicit def encCombinedMapTokenQueryParameters
    : Encoder[CombinedMapTokenQueryParameters] =
    deriveEncoder[CombinedMapTokenQueryParameters]
  implicit def decCombinedMapTokenQueryParameters
    : Decoder[CombinedMapTokenQueryParameters] =
    deriveDecoder[CombinedMapTokenQueryParameters]
}

final case class UploadQueryParameters(
    datasource: Option[UUID] = None,
    uploadStatus: Option[String] = None,
    projectId: Option[UUID] = None,
    layerId: Option[UUID] = None
)

object UploadQueryParameters {
  implicit def encUploadQueryParameters: Encoder[UploadQueryParameters] =
    deriveEncoder[UploadQueryParameters]
  implicit def decUploadQueryParameters: Decoder[UploadQueryParameters] =
    deriveDecoder[UploadQueryParameters]
}

final case class ExportQueryParameters(
    organization: Option[UUID] = None,
    project: Option[UUID] = None,
    analysis: Option[UUID] = None,
    exportStatus: Iterable[String] = Seq.empty[String],
    layer: Option[UUID] = None
)

object ExportQueryParameters {
  implicit def encExportQueryParameters: Encoder[ExportQueryParameters] =
    deriveEncoder[ExportQueryParameters]
  implicit def decExportQueryParameters: Decoder[ExportQueryParameters] =
    deriveDecoder[ExportQueryParameters]
}

final case class DropboxAuthQueryParameters(code: Option[String] = None)

object DropboxAuthQueryParameters {
  implicit def encDropboxAuthQueryParameters
    : Encoder[DropboxAuthQueryParameters] =
    deriveEncoder[DropboxAuthQueryParameters]
  implicit def decDropboxAuthQueryParameters
    : Decoder[DropboxAuthQueryParameters] =
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
    bbox: Iterable[String] = Seq.empty[String],
    withOwnerInfo: Option[Boolean] = None,
    taskId: Option[UUID] = None
) {
  val bboxPolygon: Option[Seq[Projected[Polygon]]] =
    BboxUtil.toBboxPolygon(bbox)
}

object AnnotationQueryParameters {
  implicit def encAnnotationQueryParameters
    : Encoder[AnnotationQueryParameters] =
    deriveEncoder[AnnotationQueryParameters]
  implicit def decAnnotationQueryParameters
    : Decoder[AnnotationQueryParameters] =
    deriveDecoder[AnnotationQueryParameters]
}

final case class AnnotationExportQueryParameters(
    exportAll: Option[Boolean] = None
)

object AnnotationExportQueryParameters {
  implicit def encAnnotationExportQueryParameters
    : Encoder[AnnotationExportQueryParameters] =
    deriveEncoder[AnnotationExportQueryParameters]
  implicit def decAnnotationExportQueryParameters
    : Decoder[PlatformIdQueryParameters] =
    deriveDecoder[PlatformIdQueryParameters]
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
  implicit def encShapeQueryParameters: Encoder[ShapeQueryParameters] =
    deriveEncoder[ShapeQueryParameters]
  implicit def decShapeQueryParameters: Decoder[ShapeQueryParameters] =
    deriveDecoder[ShapeQueryParameters]
}

final case class SearchQueryParameters(search: Option[String] = None)

object SearchQueryParameters {
  implicit def encSearchQueryParameters: Encoder[SearchQueryParameters] =
    deriveEncoder[SearchQueryParameters]
  implicit def decSearchQueryParameters: Decoder[SearchQueryParameters] =
    deriveDecoder[SearchQueryParameters]
}

final case class ActivationQueryParameters(isActive: Option[Boolean] = None)

object ActivationQueryParameters {
  implicit def encActivationQueryParameters
    : Encoder[ActivationQueryParameters] =
    deriveEncoder[ActivationQueryParameters]
  implicit def decActivationQueryParameters
    : Decoder[ActivationQueryParameters] =
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
  implicit def encTeamQueryParameters: Encoder[TeamQueryParameters] =
    deriveEncoder[TeamQueryParameters]
  implicit def decTeamQueryParameters: Decoder[TeamQueryParameters] =
    deriveDecoder[TeamQueryParameters]
}

final case class PlatformQueryParameters(
    timestampParams: TimestampQueryParameters = TimestampQueryParameters(),
    onlyUserParams: UserAuditQueryParameters = UserAuditQueryParameters(),
    searchParams: SearchQueryParameters = SearchQueryParameters(),
    activationParams: ActivationQueryParameters = ActivationQueryParameters()
)

object PlatformQueryParameters {
  implicit def encPlatformQueryParameters: Encoder[PlatformQueryParameters] =
    deriveEncoder[PlatformQueryParameters]
  implicit def decPlatformQueryParameters: Decoder[PlatformQueryParameters] =
    deriveDecoder[PlatformQueryParameters]
}

final case class PlatformIdQueryParameters(platformId: Option[UUID] = None)

object PlatformIdQueryParameters {
  implicit def encPlatformIdQueryParameters
    : Encoder[PlatformIdQueryParameters] =
    deriveEncoder[PlatformIdQueryParameters]
  implicit def decPlatformIdQueryParameters
    : Decoder[PlatformIdQueryParameters] =
    deriveDecoder[PlatformIdQueryParameters]
}

final case class OrganizationQueryParameters(
    timestampParams: TimestampQueryParameters = TimestampQueryParameters(),
    searchParams: SearchQueryParameters = SearchQueryParameters(),
    activationParams: ActivationQueryParameters = ActivationQueryParameters(),
    platformIdParams: PlatformIdQueryParameters = PlatformIdQueryParameters()
)

object OrganizationQueryParameters {
  implicit def encOrganizationQueryParameters
    : Encoder[OrganizationQueryParameters] =
    deriveEncoder[OrganizationQueryParameters]
  implicit def decOrganizationQueryParameters
    : Decoder[OrganizationQueryParameters] =
    deriveDecoder[OrganizationQueryParameters]
}

final case class SceneThumbnailQueryParameters(
    width: Option[Int],
    height: Option[Int],
    token: String,
    red: Option[Int],
    green: Option[Int],
    blue: Option[Int],
    floor: Option[Int]
)

object SceneThumbnailQueryParameters {
  implicit def encSceneThumbnailQueryParameters
    : Encoder[SceneThumbnailQueryParameters] =
    deriveEncoder[SceneThumbnailQueryParameters]
  implicit def decSceneThumbnailQueryParameters
    : Decoder[SceneThumbnailQueryParameters] =
    deriveDecoder[SceneThumbnailQueryParameters]
}

final case class TagQueryParameters(
    tagsInclude: Iterable[String] = Seq.empty[String],
    tagsExclude: Iterable[String] = Seq.empty[String]
)

object TagQueryParameters {
  implicit def encTagQueryParameters: Encoder[TagQueryParameters] =
    deriveEncoder[TagQueryParameters]
  implicit def decTagQueryParameters: Decoder[TagQueryParameters] =
    deriveDecoder[TagQueryParameters]
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

final case class MetricQueryParameters(
    projectId: Option[UUID] = None,
    projectLayerId: Option[UUID] = None,
    analysisId: Option[UUID] = None,
    nodeId: Option[UUID] = None,
    referer: Option[String] = None,
    requestType: MetricRequestType
)

final case class TaskQueryParameters(
    status: Option[TaskStatus] = None,
    locked: Option[Boolean] = None,
    lockedBy: Option[String] = None,
    bbox: Iterable[String] = Seq.empty,
    actionUser: Option[String] = None,
    actionType: Option[TaskStatus] = None,
    actionStartTime: Option[Timestamp] = None,
    actionEndTime: Option[Timestamp] = None,
    actionMinCount: Option[Int] = None,
    actionMaxCount: Option[Int] = None,
    format: Option[String] = None
) {
  val bboxPolygon: Option[Seq[Projected[Polygon]]] =
    BboxUtil.toBboxPolygon(bbox)
}

final case class UserTaskActivityParameters(
    actionStartTime: Option[Timestamp] = None,
    actionEndTime: Option[Timestamp] = None,
    actionUser: Option[String] = None
)

object UserTaskActivityParameters {
  implicit def encUserTaskActivityParameters
    : Encoder[UserTaskActivityParameters] =
    deriveEncoder[UserTaskActivityParameters]
  implicit def decUserTaskActivityParameters
    : Decoder[UserTaskActivityParameters] =
    deriveDecoder[UserTaskActivityParameters]
}

final case class StacExportQueryParameters(
    userParams: UserAuditQueryParameters = UserAuditQueryParameters(),
    ownerParams: OwnerQueryParameters = OwnerQueryParameters(),
    searchParams: SearchQueryParameters = SearchQueryParameters(),
    exportStatus: Option[String] = None,
    annotationProjectId: Option[UUID] = None
)

object StacExportQueryParameters {
  implicit def encStacExportQueryParameters
    : Encoder[StacExportQueryParameters] =
    deriveEncoder[StacExportQueryParameters]
  implicit def decStacExportQueryParameters
    : Decoder[StacExportQueryParameters] =
    deriveDecoder[StacExportQueryParameters]
}

final case class AnnotationProjectFilterQueryParameters(
    projectType: Option[AnnotationProjectType] = None,
    taskStatusesInclude: Iterable[TaskStatus] = Seq.empty[TaskStatus]
)

object AnnotationProjectFilterQueryParameters {
  implicit def encGroupQueryParameters
    : Encoder[AnnotationProjectFilterQueryParameters] =
    deriveEncoder[AnnotationProjectFilterQueryParameters]
  implicit def decGroupQueryParameters
    : Decoder[AnnotationProjectFilterQueryParameters] =
    deriveDecoder[AnnotationProjectFilterQueryParameters]
}

final case class AnnotationProjectQueryParameters(
    ownerParams: OwnerQueryParameters = OwnerQueryParameters(),
    searchParams: SearchQueryParameters = SearchQueryParameters(),
    ownershipTypeParams: OwnershipTypeQueryParameters =
      OwnershipTypeQueryParameters(),
    groupQueryParameters: GroupQueryParameters = GroupQueryParameters(),
    projectFilterParams: AnnotationProjectFilterQueryParameters =
      AnnotationProjectFilterQueryParameters()
)

object AnnotationProjectQueryParameters {
  implicit def encGroupQueryParameters
    : Encoder[AnnotationProjectQueryParameters] =
    deriveEncoder[AnnotationProjectQueryParameters]
  implicit def decGroupQueryParameters
    : Decoder[AnnotationProjectQueryParameters] =
    deriveDecoder[AnnotationProjectQueryParameters]
}
