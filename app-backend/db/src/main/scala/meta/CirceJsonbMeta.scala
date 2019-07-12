package com.rasterfoundry.database.meta

import com.rasterfoundry.datamodel._
import com.rasterfoundry.common.color._

import doobie._
import doobie.postgres.circe.jsonb.implicits._
import cats.syntax.either._
import io.circe._
import io.circe.syntax._

import scala.reflect.runtime.universe.TypeTag
import java.net.URI

object CirceJsonbMeta {
  def apply[Type: TypeTag: Encoder: Decoder] = {
    val get = Get[Json].tmap[Type](_.as[Type].valueOr(throw _))
    val put = Put[Json].tcontramap[Type](_.asJson)
    new Meta[Type](get, put)
  }
}

trait CirceJsonbMeta {
  implicit val compositeMeta: Meta[Map[String, ColorComposite]] =
    CirceJsonbMeta[Map[String, ColorComposite]]

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

  implicit val uriMeta: Meta[URI] =
    CirceJsonbMeta[URI]

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

  implicit val stacExportLayerDefinitionMeta
    : Meta[List[StacExport.LayerDefinition]] =
    CirceJsonbMeta[List[StacExport.LayerDefinition]]

  implicit val taskStatusListMeta: Meta[List[TaskStatus]] =
    CirceJsonbMeta[List[TaskStatus]]
}
