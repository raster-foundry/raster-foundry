package com.azavea.rf.datamodel

import java.net.{URI, URLEncoder}
import java.sql.Timestamp
import java.util.Date
import java.util.UUID

import io.circe._
import io.circe.generic.JsonCodec
import cats.syntax.either._
import io.circe.generic.semiauto._

sealed abstract class UserRole(val repr: String) extends Product with Serializable
case object UserRoleRole extends UserRole("USER")
case object Viewer extends UserRole("VIEWER")
case object Admin extends UserRole("ADMIN")

object UserRole {
  implicit val userRoleEncoder: Encoder[UserRole] =
    Encoder.encodeString.contramap[UserRole](_.toString)
  implicit val userRoleDecoder: Decoder[UserRole] =
    Decoder.decodeString.emap { str =>
      Either.catchNonFatal(UserRole.fromString(str)).leftMap(_ => "UserRole")
    }

  def fromString(s: String) = s.toUpperCase match {
    case "USER" => UserRoleRole  // TODO Think of a better name than UserRoleRole
    case "VIEWER" => Viewer
    case "ADMIN" => Admin
    case _ => throw new Exception(s"Unsupported user role string: $s")
  }

  def toString(ur: UserRole): String = {
    ur match {
      case UserRoleRole => "USER"
      case Viewer => "VIEWER"
      case Admin => "ADMIN"
    }
  }
}

case class UserOptionAndRoles(user: Option[User], roles: List[UserGroupRole])

case class Credential(token: Option[String])

object Credential {
  implicit val credentialEncoder: Encoder[Credential] =
    Encoder.encodeString.contramap[Credential] { _.token.getOrElse("") }
  implicit val credentialDecoder: Decoder[Credential] =
    Decoder.decodeString.emap[Credential] { str =>
      Either.catchNonFatal(Credential.fromString(str)).leftMap(_ => "Credential")
    }

  def fromString(s: String) = {
    Credential.apply(Some(s))
  }

  def fromStringO(s: Option[String]) = {
    Credential.apply(s)
  }
}

case class User(
  id: String,
  role: UserRole,
  createdAt: Timestamp,
  modifiedAt: Timestamp,
  dropboxCredential: Credential,
  planetCredential: Credential,
  emailNotifications: Boolean,
  email: String,
  name: String,
  profileImageUri: String,
  isSuperuser: Boolean,
  isActive: Boolean,
  visibility: UserVisibility
) {
  private val rootOrganizationId = UUID.fromString("9e2bef18-3f46-426b-a5bd-9913ee1ff840")

  def getDefaultExportSource(export: Export, dataBucket: String): URI =
    new URI(s"s3://$dataBucket/user-exports/${URLEncoder.encode(id, "UTF-8")}/${export.id}")

  def getDefaultAnnotationShapefileSource(dataBucket: String): URI =
    new URI(s"s3://$dataBucket/user-exports/${URLEncoder.encode(id, "UTF-8")}/annotations/${UUID.randomUUID}/annotations.zip")
}

object User {
  def tupled = (User.apply _).tupled

  def create = Create.apply _


  implicit val decodeUser: Decoder[User] = deriveDecoder[User]

  implicit val encodeUser: Encoder[User] = Encoder.forProduct4(
    "id", "name", "email", "profileImageUri"
  )(u => (u.id, u.name, u.email, u.profileImageUri))

  @JsonCodec
  case class WithGroupRole (
    id: String,
    role: UserRole,
    createdAt: Timestamp,
    modifiedAt: Timestamp,
    dropboxCredential: Credential,
    planetCredential: Credential,
    emailNotifications: Boolean,
    email: String,
    name: String,
    profileImageUri: String,
    isSuperuser: Boolean,
    isActive: Boolean,
    visibility: UserVisibility,
    groupRole: GroupRole
  )

  @JsonCodec
  case class Create(
    id: String,
    role: UserRole = Viewer,
    email: String = "",
    name: String = "",
    profileImageUri: String = ""
  ) {
    def toUser: User = {
      val now = new Timestamp((new java.util.Date()).getTime())
      User(
        id,
        role,
        now,
        now,
        Credential(None),
        Credential(None),
        false,
        email,
        name,
        profileImageUri,
        false, //isSuperuser
        true, //isActive
        UserVisibility.Private
      )
    }
  }

  case class JwtFields(
    id: String,
    email: String,
    name: String,
    picture: String,
    platformId: UUID,
    organizationId: UUID
  ) {
    def toUser: User = {
      val now = new Timestamp((new java.util.Date()).getTime())
      User(
        id,
        Viewer,
        now,
        now,
        Credential(None),
        Credential(None),
        false,
        email,
        name,
        picture,
        false, //isSuperuser
        true, //isActive
        UserVisibility.Private
      )
    }
  }
}
