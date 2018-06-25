package com.azavea.rf.datamodel

import java.util.UUID
import java.sql.Timestamp

import io.circe._
import io.circe.generic.JsonCodec

@JsonCodec
case class Organization(
  id: UUID,
  createdAt: Timestamp,
  modifiedAt: Timestamp,
  name: String,
  platformId: UUID,
  status: OrgStatus,
  dropboxCredential: Credential,
  planetCredential: Credential,
  logoUri: String,
  visibility: Visibility
)

object Organization {
  def tupled = (Organization.apply _).tupled

  def create = Create.apply _

  @JsonCodec
  case class Create(
    name: String,
    platformId: UUID,
    visibility: Option[Visibility],
    status: OrgStatus
  ) {
    def toOrganization(isAdmin: Boolean): Organization = {
      val id = java.util.UUID.randomUUID()
      val now = new Timestamp((new java.util.Date()).getTime())

      val finalStatus = isAdmin match {
        case true => status
        case _ => OrgStatus.Requested
      }

      Organization(id, now, now, name, platformId, finalStatus, Credential(None), Credential(None), "", visibility.getOrElse(Visibility.Private))
    }
  }

}
