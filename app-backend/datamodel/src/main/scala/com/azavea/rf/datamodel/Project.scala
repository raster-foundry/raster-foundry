package com.azavea.rf.datamodel

import geotrellis.vector.io.json.GeoJsonSupport
import geotrellis.vector.{Geometry, Point}
import geotrellis.slick.Projected

import spray.json._
import spray.json.DefaultJsonProtocol._
import java.util.UUID
import java.sql.Timestamp

case class Project(
  id: UUID,
  createdAt: Timestamp,
  modifiedAt: Timestamp,
  organizationId: UUID,
  createdBy: String,
  modifiedBy: String,
  name: String,
  slugLabel: String,
  description: String,
  visibility: Visibility,
  tags: List[String] = List.empty,
  extent: Option[Projected[Geometry]] = None,
  manualOrder: Boolean = true
)

/** Case class for project creation */
object Project extends GeoJsonSupport {

  def tupled = (Project.apply _).tupled

  def create = Create.apply _

  implicit val defaultProjectFormat = jsonFormat13(Project.apply _)

  def slugify(input: String): String = {
    import java.text.Normalizer
    Normalizer.normalize(input, Normalizer.Form.NFD)
      .replaceAll("[^\\w\\s-]", ""
        .replace('-', ' ')
        .trim
        .replaceAll("\\s+", "-")
        .toLowerCase)
  }

  case class Create(
    organizationId: UUID,
    name: String,
    description: String,
    visibility: Visibility,
    tags: List[String]
  ) {
    def toProject(userId: String): Project = {
      val now = new Timestamp((new java.util.Date()).getTime())
      Project(
        UUID.randomUUID, // primary key
        now, // createdAt
        now, // modifiedAt
        organizationId,
        userId, // createdBy
        userId, // modifiedBy
        name,
        slugify(name),
        description,
        visibility,
        tags
      )
    }
  }

  object Create {
    implicit val defaultProjectFormat = jsonFormat5(Create.apply _)
  }
}
