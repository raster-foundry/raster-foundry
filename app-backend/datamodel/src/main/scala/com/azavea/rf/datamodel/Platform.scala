package com.azavea.rf.datamodel

import java.util.UUID
import java.sql.Timestamp

import io.circe._
import io.circe.generic.JsonCodec
import io.circe.syntax._
import io.circe.generic.semiauto._
import cats.syntax.either._


case class Platform(
  id: UUID,
  name: String,
  publicSettings: Platform.PublicSettings,
  isActive: Boolean,
  defaultOrganizationId: Option[UUID],
  privateSettings: Platform.PrivateSettings
)

object Platform {

  def tupled = (Platform.apply _).tupled

  case class PublicSettings(
    emailUser: String,
    emailSmtpHost: String,
    emailIngestNotification: Boolean,
    emailAoiNotification: Boolean
  )

  case class PrivateSettings(emailPassword: String)

  implicit val encodePublicSettings: Encoder[PublicSettings] = new Encoder[PublicSettings] {
    final def apply(a: PublicSettings): Json = Json.obj(
      ("emailUser", Json.fromString(a.emailUser)),
      ("emailSmtpHost", Json.fromString(a.emailSmtpHost)),
      ("emailIngestNotification", Json.fromBoolean(a.emailIngestNotification)),
      ("emailAoiNotification", Json.fromBoolean(a.emailAoiNotification))
    )
  }

  implicit val decodePublicSettings: Decoder[PublicSettings] = new Decoder[PublicSettings] {
    final def apply(c: HCursor): Decoder.Result[PublicSettings] =
      for {
        emailUser <- c.downField("emailUser").as[String]
        emailSmtpHost <- c.downField("emailSmtpHost").as[String]
        emailIngestNotification <- c.downField("emailIngestNotification").as[Boolean]
        emailAoiNotification <- c.downField("emailAoiNotification").as[Boolean]
      } yield {
        new PublicSettings(emailUser, emailSmtpHost, emailIngestNotification, emailAoiNotification)
      }
  }

  implicit val encodePrivateSettings: Encoder[PrivateSettings] = new Encoder[PrivateSettings] {
    final def apply(a: PrivateSettings): Json = Json.obj(
      ("emailPassword", Json.fromString(a.emailPassword))
    )
  }

  implicit val decodePrivateSettings: Decoder[PrivateSettings] = new Decoder[PrivateSettings] {
    final def apply(c: HCursor): Decoder.Result[PrivateSettings] =
      for {
        emailPassword <- c.downField("emailPassword").as[String]
      } yield {
        new PrivateSettings(emailPassword)
      }
  }

  implicit val decodePlatform: Decoder[Platform] = deriveDecoder[Platform]

  implicit val encodePlatform: Encoder[Platform] = Encoder.forProduct5(
       "id", "name", "publicSettings", "isActive", "defaultOrganizationId"
    )(s => (s.id, s.name, s.publicSettings, s.isActive, s.defaultOrganizationId))
}
