package com.rasterfoundry.database.meta

import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import doobie.util.invariant.InvalidObjectMapping
import doobie.postgres.circe.jsonb.implicits._
import cats._
import cats.data._
import cats.effect.IO
import cats.syntax.either._
import io.circe._
import io.circe.syntax._
import org.postgresql.util.PGobject
import com.rasterfoundry.datamodel._
import com.rasterfoundry.datamodel.ColorCorrect._

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
}
