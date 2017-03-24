package com.azavea.rf.datamodel

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
) {
  private val rootOrganizationId = UUID.fromString("9e2bef18-3f46-426b-a5bd-9913ee1ff840")

  def isInRootOrganization: Boolean = {
    this.organizationId == rootOrganizationId
  }

  def isInRootOrSameOrganizationAs(target: { def organizationId: UUID }): Boolean = {
    this.isInRootOrganization || this.organizationId == target.organizationId
  }
}

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


