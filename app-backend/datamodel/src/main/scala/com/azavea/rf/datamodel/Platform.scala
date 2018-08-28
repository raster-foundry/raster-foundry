package com.azavea.rf.datamodel

import java.util.UUID

import io.circe._
import io.circe.generic.JsonCodec
import io.circe.generic.semiauto._

final case class Platform(id: UUID,
                          name: String,
                          publicSettings: Platform.PublicSettings,
                          isActive: Boolean,
                          defaultOrganizationId: Option[UUID],
                          privateSettings: Platform.PrivateSettings)

object Platform {

  def tupled = (Platform.apply _).tupled

  @JsonCodec
  final case class PublicSettings(emailUser: String,
                                  emailSmtpHost: String,
                                  emailSmtpPort: Int,
                                  emailSmtpEncryption: String,
                                  emailIngestNotification: Boolean,
                                  emailAoiNotification: Boolean,
                                  emailExportNotification: Boolean,
                                  platformHost: Option[String])

  @JsonCodec
  final case class PrivateSettings(emailPassword: String)

  implicit val decodePlatform: Decoder[Platform] = deriveDecoder[Platform]

  implicit val encodePlatform: Encoder[Platform] = Encoder.forProduct5(
    "id",
    "name",
    "publicSettings",
    "isActive",
    "defaultOrganizationId"
  )(s => (s.id, s.name, s.publicSettings, s.isActive, s.defaultOrganizationId))
}

@JsonCodec
final case class PlatformWithUsersSceneProjects(
    platId: UUID,
    platName: String,
    uId: String,
    uName: String,
    pubSettings: Platform.PublicSettings,
    priSettings: Platform.PrivateSettings,
    email: String,
    emailNotifications: Boolean,
    projectId: UUID,
    projectName: String,
    personalInfo: User.PersonalInfo
) {
  def getUserEmail: String =
    (emailNotifications, personalInfo.emailNotifications) match {
      case (true, true) | (false, true) => personalInfo.email
      case (true, false)                => email
      case (false, false)               => ""
    }
}

@JsonCodec
final case class PlatformWithSceneOwner(platId: UUID,
                                        platName: String,
                                        uId: String,
                                        uName: String,
                                        pubSettings: Platform.PublicSettings,
                                        priSettings: Platform.PrivateSettings,
                                        email: String,
                                        emailNotifications: Boolean,
                                        personalInfo: User.PersonalInfo) {
  def getUserEmail: String =
    (emailNotifications, personalInfo.emailNotifications) match {
      case (true, true) | (false, true) => personalInfo.email
      case (true, false)                => email
      case (false, false)               => ""
    }
}
