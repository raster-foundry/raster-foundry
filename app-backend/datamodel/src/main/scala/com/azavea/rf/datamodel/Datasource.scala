package com.azavea.rf.datamodel

import spray.json._
import spray.json.DefaultJsonProtocol._
import java.util.UUID
import java.sql.Timestamp

case class Datasource(
  id: UUID,
  createdAt: java.sql.Timestamp,
  createdBy: String,
  modifiedAt: java.sql.Timestamp,
  modifiedBy: String,
  organizationId: UUID,
  name: String,
  visibility: Visibility,
  colorCorrection: Map[String, Any],
  composites: Map[String, Any],
  extras: Map[String, Any]
)

object Datasource {

  implicit val defaultDatasourceFormat = jsonFormat11(Datasource.apply _)

  def tupled = (Datasource.apply _).tupled

  def create = Create.apply _


  case class Create(
    organizationId: UUID,
    name: String,
    visibility: Visibility,
    colorCorrection: Map[String, Any],
    composites: Map[String, Any],
    extras: Map[String, Any]
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

  object Create {
    implicit val defaultDatasourceCreateFormat = jsonFormat6(Create.apply _)
  }
}
