package com.rasterfoundry.database.meta

import com.rasterfoundry.common.BacksplashGeoTiffInfo
import com.rasterfoundry.common.color._
import com.rasterfoundry.datamodel._

import cats.implicits._
import doobie._
import doobie.postgres.circe.jsonb.implicits._
import geotrellis.raster.{CellSize, GridExtent}
import io.circe._
import io.circe.syntax._

import scala.reflect.runtime.universe.TypeTag

object CirceJsonbMeta {
  def apply[Type: TypeTag: Encoder: Decoder] = {
    val get = Get[Json].tmap[Type](_.as[Type].valueOr(throw _))
    val put = Put[Json].tcontramap[Type](_.asJson)
    new Meta[Type](get, put)
  }
}

trait CirceJsonbMeta {

  implicit val cellSizeEncoder: Encoder[CellSize] =
    (a: CellSize) =>
      Json.obj(
        ("width", a.width.asJson),
        ("height", a.height.asJson)
    )

  implicit val cellSizeDecoder: Decoder[CellSize] = (c: HCursor) =>
    for {
      width <- c.downField("width").as[Double]
      height <- c.downField("height").as[Double]
    } yield {
      new CellSize(width, height)
  }

  implicit val gridExtentMeta: Meta[GridExtent[Long]] =
    CirceJsonbMeta[GridExtent[Long]]
  implicit val gridExtentListMeta: Meta[List[GridExtent[Long]]] =
    CirceJsonbMeta[List[GridExtent[Long]]]

  implicit val mapMeta: Meta[Map[String, String]] =
    CirceJsonbMeta[Map[String, String]]

  implicit val compositeMeta: Meta[Map[String, ColorComposite]] =
    CirceJsonbMeta[Map[String, ColorComposite]]

  implicit val cellSizeMeta: Meta[List[CellSize]] =
    CirceJsonbMeta[List[CellSize]]

  implicit val credentialMeta: Meta[Credential] =
    CirceJsonbMeta[Credential]

  implicit val colorCorrectionMeta: Meta[ColorCorrect.Params] =
    CirceJsonbMeta[ColorCorrect.Params]

  implicit val thumbnailMeta: Meta[List[Thumbnail]] =
    CirceJsonbMeta[List[Thumbnail]]

  implicit val imageWithRelated: Meta[List[Image.WithRelated]] =
    CirceJsonbMeta[List[Image.WithRelated]]

  implicit val bandMeta: Meta[List[Band]] =
    CirceJsonbMeta[List[Band]]

  implicit val PlatformPublicSettingsMeta: Meta[Platform.PublicSettings] =
    CirceJsonbMeta[Platform.PublicSettings]

  implicit val PlatformPrivateSettingsMeta: Meta[Platform.PrivateSettings] =
    CirceJsonbMeta[Platform.PrivateSettings]

  implicit val UserPersonalInfoMeta: Meta[User.PersonalInfo] =
    CirceJsonbMeta[User.PersonalInfo]

  implicit val singleBandOptionsMeta: Meta[SingleBandOptions.Params] =
    CirceJsonbMeta[SingleBandOptions.Params]

  implicit val jsonMeta: Meta[Json] =
    CirceJsonbMeta[Json]

  implicit val metricEventMeta: Meta[MetricEvent] =
    CirceJsonbMeta[MetricEvent]

  implicit val taskActionStampMeta: Meta[List[TaskActionStamp]] =
    CirceJsonbMeta[List[TaskActionStamp]]

  implicit val taskFeatureMeta: Meta[List[Task.TaskFeature]] =
    CirceJsonbMeta[List[Task.TaskFeature]]

  implicit val userScopeMeta: Meta[Map[ObjectType, List[ActionType]]] =
    CirceJsonbMeta[Map[ObjectType, List[ActionType]]]

  implicit val taskStatusListMeta: Meta[List[TaskStatus]] =
    CirceJsonbMeta[List[TaskStatus]]

  implicit val backsplashGeoTiffInfoMeta: Meta[BacksplashGeoTiffInfo] =
    CirceJsonbMeta[BacksplashGeoTiffInfo]

  implicit val stacExportLicenseMeta: Meta[StacExportLicense] =
    CirceJsonbMeta[StacExportLicense]

  implicit val scopesMeta: Meta[Scope] = CirceJsonbMeta[Scope]

  implicit val annotationProjectTilesMeta: Meta[List[TileLayer]] =
    CirceJsonbMeta[List[TileLayer]]

  implicit val annotationProjectLabelGroupsMeta
    : Meta[List[AnnotationLabelClassGroup.WithLabelClasses]] =
    CirceJsonbMeta[List[AnnotationLabelClassGroup.WithLabelClasses]]

  implicit val annotationProjectTaskStatusSummaryMeta: Meta[Map[String, Int]] =
    CirceJsonbMeta[Map[String, Int]]
}
