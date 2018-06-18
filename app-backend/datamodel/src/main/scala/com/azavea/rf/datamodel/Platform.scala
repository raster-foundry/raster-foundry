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

  @JsonCodec
  case class PublicSettings(
    emailUser: String,
    emailSmtpHost: String,
    emailIngestNotification: Boolean,
    emailAoiNotification: Boolean
  )

  @JsonCodec
  case class PrivateSettings(emailPassword: String)

  implicit val decodePlatform: Decoder[Platform] = deriveDecoder[Platform]

  implicit val encodePlatform: Encoder[Platform] = Encoder.forProduct5(
    "id", "name", "publicSettings", "isActive", "defaultOrganizationId"
  )(s => (s.id, s.name, s.publicSettings, s.isActive, s.defaultOrganizationId))
}

@JsonCodec
case class PlatformWithUsersSceneProjects(
  platId: UUID,
  platName: String,
  uId: String,
  uName: String,
  pubSettings: Platform.PublicSettings,
  priSettings: Platform.PrivateSettings,
  email: String,
  emailNotifications: Boolean,
  projectId: UUID,
  projectName: String
)

@JsonCodec
case class PlatformWithSceneOwner(
  platId: UUID,
  platName: String,
  uId: String,
  uName: String,
  pubSettings: Platform.PublicSettings,
  priSettings: Platform.PrivateSettings,
  email: String,
  emailNotifications: Boolean
)
