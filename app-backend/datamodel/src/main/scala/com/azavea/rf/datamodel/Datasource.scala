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
  organizationId: UUID,
  name: String,
  visibility: Visibility,
  colorCorrection: Json,
  composites: Json,
  extras: Json
)

object Datasource {

  def tupled = (Datasource.apply _).tupled

  def create = Create.apply _


  @JsonCodec
  case class Create(
    organizationId: UUID,
    name: String,
    visibility: Visibility,
    colorCorrection: Json,
    composites: Json,
    extras: Json
  ) {
    def toDatasource(userId: String): Datasource = {
      val id = java.util.UUID.randomUUID()
      val now = new Timestamp((new java.util.Date()).getTime())
      Datasource(
        id,
        now, // createdAt
        userId, // createdBy
        now, // modifiedAt
        userId, // modifiedBy
        this.organizationId,
        this.name,
        this.visibility,
        this.colorCorrection,
        this.composites,
        this.extras
      )
    }
  }
}
