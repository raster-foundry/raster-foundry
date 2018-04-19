package com.azavea.rf.datamodel

import java.net.{URI, URLEncoder}
import java.sql.Timestamp
import java.util.Date
import java.util.UUID

import io.circe._
import io.circe.generic.JsonCodec
import cats.syntax.either._

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

@JsonCodec
case class User(
  id: String,
  organizationId: UUID,
  role: UserRole,
  createdAt: Timestamp,
  modifiedAt: Timestamp,
  dropboxCredential: Credential,
  planetCredential: Credential,
  emailNotifications: Boolean
) {
  private val rootOrganizationId = UUID.fromString("9e2bef18-3f46-426b-a5bd-9913ee1ff840")

  def isInRootOrganization: Boolean = {
    this.organizationId == rootOrganizationId
  }

  def isInRootOrSameOrganizationAs(target: { def organizationId: UUID }): Boolean = {
    this.isInRootOrganization || this.organizationId == target.organizationId
  }

  def isInRootOrOwner(target: { def owner: String }): Boolean = {
    this.isInRootOrganization || this.id == target.owner
  }

  def getDefaultExportSource(export: Export, dataBucket: String): URI =
    new URI(s"s3://$dataBucket/user-exports/${URLEncoder.encode(id, "UTF-8")}/${export.id}")

  def getDefaultAnnotationShapefileSource(dataBucket: String): URI =
    new URI(s"s3://$dataBucket/user-exports/${URLEncoder.encode(id, "UTF-8")}/annotations/${UUID.randomUUID}/annotations.zip")
}

object User {
  def tupled = (User.apply _).tupled

  def create = Create.apply _

  @JsonCodec
  case class Create(
    id: String,
    organizationId: UUID,
    role: UserRole = Viewer
  ) {
    def toUser: User = {
      val now = new Timestamp((new java.util.Date()).getTime())
      User(id, organizationId, role, now, now, Credential(None), Credential(None), false)
    }
  }
}
