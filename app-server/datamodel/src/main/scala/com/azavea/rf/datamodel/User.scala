package com.azavea.rf.datamodel

import java.sql.Timestamp
import java.util.UUID

case class User(id: String)

case class UserToOrganization(
  userId: String,
  organizationId: UUID,
  role: String,
  createdAt: Timestamp,
  modifiedAt: Timestamp
)

case class UserCreate(id: String, organizationId: UUID, role: Option[String] = None) {
  def toUsersOrgTuple(): (User, UserToOrganization)= {
    val now = new Timestamp((new java.util.Date()).getTime())
    val newUUID = java.util.UUID.randomUUID
    val user = User(
      id=id
    )
    val defaultedRole = role match {
      case Some(UserRoles(role)) => role
      case Some(userOrgRoleJoins: String) =>
        throw new IllegalArgumentException( "\"" + userOrgRoleJoins + "\" is not a valid user Role")
      case None => UserRoles.User
    }

    val userToOrg = UserToOrganization(
      userId=id,
      organizationId=organizationId,
      defaultedRole.toString(),
      now,
      now
    )
    (user, userToOrg)
  }
}

case class OrganizationWithRole(id: java.util.UUID, name: String, role: String)

case class UserRoleOrgJoin(userId: String, orgId: java.util.UUID, orgName: String, userRole: String)

case class UserWithOrgs(id: String, organizations: Seq[OrganizationWithRole])

object UserRoles extends Enumeration {
  val User = roleValue("user")
  val Viewer = roleValue("viewer")
  val Admin = roleValue("admin")

  def roleValue(name: String): Value with Matching =
    new Val(nextId, name) with Matching

  def unapply(s: String): Option[Value] =
    values.find(s == _.toString)

  trait Matching {
    def unapply(s: String): Boolean =
      (s == toString)
  }
}
