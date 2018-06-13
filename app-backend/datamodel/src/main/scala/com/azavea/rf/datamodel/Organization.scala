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
  isActive: Boolean,
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
    visibility: Option[Visibility]
  ) {
    def toOrganization: Organization = {
      val id = java.util.UUID.randomUUID()
      val now = new Timestamp((new java.util.Date()).getTime())
      Organization(id, now, now, name, platformId, true, Credential(None), Credential(None), "", visibility.getOrElse(Visibility.Private))
    }
  }

}
