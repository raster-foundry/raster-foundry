package com.azavea.rf.datamodel

import java.util.UUID
import java.sql.Timestamp

case class Organization(
  id: UUID,
  createdAt: Timestamp,
  modifiedAt: Timestamp,
  name: String
)

case class UserWithRole(id: String, role: String, createdAt: Timestamp, modifiedAt: Timestamp)

case class UserWithRoleCreate(id: String, role: String) {
  def toUserWithRole(): UserWithRole = {
    val now = new Timestamp((new java.util.Date()).getTime())
    UserWithRole(id, role, now, now)
  }
}

case class OrganizationCreate(name: String) {
  def toOrganization(): Organization = {
    val id = java.util.UUID.randomUUID()
    val now = new Timestamp((new java.util.Date()).getTime())
    Organization(id, now, now, name)
  }
}
