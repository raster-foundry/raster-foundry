package com.azavea.rf.datamodel

import spray.json._
import spray.json.DefaultJsonProtocol._
import java.sql.Timestamp
import java.util.UUID

sealed abstract class UserRole(val repr: String) extends Product with Serializable
case object UserRoleRole extends UserRole("USER")
case object Viewer extends UserRole("VIEWER")
case object Admin extends UserRole("ADMIN")

object UserRole {
  def fromString(s: String) = s.toUpperCase match {
    case "USER" => UserRoleRole  // TODO Think of a better name than UserRoleRole
    case "VIEWER" => Viewer
    case "ADMIN" => Admin
    case _ => throw new Exception(s"Unsupported user role string: $s")
  }

  implicit object DefaultJobStatusJsonFormat extends RootJsonFormat[UserRole] {
    def write(role: UserRole): JsValue = JsString(role.toString)
    def read(js: JsValue): UserRole = js match {
      case JsString(js) => fromString(js)
      case _ =>
        deserializationError("Failed to parse thumbnail size string representation (${js}) to JobStatus")
    }
  }
}

case class User(
  id: String,
  organizationId: UUID,
  role: UserRole,
  createdAt: Timestamp,
  modifiedAt: Timestamp
)


object User {

  def tupled = (User.apply _).tupled

  def create = Create.apply _

  implicit val defaultUserFormat = jsonFormat5(User.apply _)

  case class Create(
    id: String,
    organizationId: UUID,
    role: UserRole = Viewer
  ) {
    def toUser(): User = {
      val now = new Timestamp((new java.util.Date()).getTime())
      User(id, organizationId, role, now, now)
    }
  }
  object Create {
    implicit val defaultUserCreateFormat = jsonFormat3(Create.apply _)
  }
}


