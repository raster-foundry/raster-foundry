package com.azavea.rf.datamodel

import io.circe._
import java.util.UUID
import java.sql.Timestamp

import io.circe.generic.JsonCodec

@JsonCodec
case class Datasource(
  id: UUID,
  createdAt: java.sql.Timestamp,
  createdBy: String,
  modifiedAt: java.sql.Timestamp,
  modifiedBy: String,
  owner: String,
  organizationId: UUID,
  name: String,
  visibility: Visibility,
  composites: Json,
  extras: Json,
  bands: Json,
  licenseName: Option[String]
)

object Datasource {

  def tupled = (Datasource.apply _).tupled

  def create = Create.apply _

  @JsonCodec
  case class Create (
    organizationId: UUID,
    name: String,
    visibility: Visibility,
    owner: Option[String],
    composites: Json,
    extras: Json,
    bands: Json,
    licenseName: Option[String]
  ) extends OwnerCheck  {
    def toDatasource(user: User): Datasource = {
      val id = java.util.UUID.randomUUID()
      val now = new Timestamp((new java.util.Date()).getTime())

      val ownerId = checkOwner(user, this.owner)

      Datasource(
        id,
        now, // createdAt
        user.id, // createdBy
        now, // modifiedAt
        user.id, // modifiedBy
        ownerId, // owner
        this.organizationId,
        this.name,
        this.visibility,
        this.composites,
        this.extras,
        this.bands,
        this.licenseName
      )
    }
  }
}
