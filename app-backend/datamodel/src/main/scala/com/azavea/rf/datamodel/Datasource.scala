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
  extras: Map[String, Any]
)

object Datasource {

  def tupled = (Datasource.apply _).tupled

  def create = Create.apply _

  implicit val defaultDatasourceFormat = jsonFormat10(Datasource.apply _)

  case class Create(
    organizationId: UUID,
    name: String,
    visibility: Visibility,
    colorCorrection: Map[String, Any],
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
        this.extras
      )
    }
  }

  object Create {
    implicit val defaultDatasourceCreateFormat = jsonFormat5(Create.apply _)
  }
}
