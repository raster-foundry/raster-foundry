package com.azavea.rf.datamodel

import java.sql.Timestamp
import java.util.UUID

import io.circe.generic.JsonCodec

@JsonCodec
final case class Organization(id: UUID,
                              createdAt: Timestamp,
                              modifiedAt: Timestamp,
                              name: String,
                              platformId: UUID,
                              status: OrgStatus,
                              dropboxCredential: Credential,
                              planetCredential: Credential,
                              logoUri: String,
                              visibility: Visibility)

object Organization {
  def tupled = (Organization.apply _).tupled

  def create = Create.apply _

  @JsonCodec
  final case class Create(name: String,
                          platformId: UUID,
                          visibility: Option[Visibility],
                          status: OrgStatus) {
    def toOrganization(isAdmin: Boolean): Organization = {
      val id = java.util.UUID.randomUUID()
      val now = new Timestamp(new java.util.Date().getTime)

      val finalStatus = if (isAdmin) {
        status
      } else {
        OrgStatus.Requested
      }

      Organization(
        id,
        now,
        now,
        name,
        platformId,
        finalStatus,
        Credential(None),
        Credential(None),
        "",
        visibility.getOrElse(Visibility.Private)
      )
    }
  }

}
