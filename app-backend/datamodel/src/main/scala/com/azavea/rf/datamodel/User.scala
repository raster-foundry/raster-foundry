package com.azavea.rf.datamodel

import io.circe._
import cats.syntax.either._

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

  case class Create(
    id: String,
    organizationId: UUID,
    role: UserRole = Viewer
  ) {
    def toUser: User = {
      val now = new Timestamp((new java.util.Date()).getTime())
      User(id, organizationId, role, now, now)
    }
  }
}


