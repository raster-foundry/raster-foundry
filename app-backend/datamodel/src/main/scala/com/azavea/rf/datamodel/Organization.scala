package com.azavea.rf.datamodel

import spray.json._
import DefaultJsonProtocol._
import java.util.UUID
import java.sql.Timestamp

case class Organization(
  id: UUID,
  createdAt: Timestamp,
  modifiedAt: Timestamp,
  name: String
)

object Organization {

  def tupled = (Organization.apply _).tupled

  def create = Create.apply _

  implicit val defaultOrganizationFormat = jsonFormat4(Organization.apply _)

  case class WithRole(id: java.util.UUID, name: String, role: User.Role)
  object WithRole {
    implicit val defaultOrgRoleFormat = jsonFormat3(Organization.WithRole.apply _)
  }

  case class Create(name: String) {
    def toOrganization(): Organization = {
      val id = java.util.UUID.randomUUID()
      val now = new Timestamp((new java.util.Date()).getTime())
      Organization(id, now, now, name)
    }
  }

  object Create {
    implicit val defaultOrganizationCreateFormat = jsonFormat1(Create.apply _)
  }
}
