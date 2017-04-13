package com.azavea.rf.datamodel

import java.sql.Timestamp
import java.util.UUID

import geotrellis.slick.Projected
import geotrellis.vector.Geometry
import geotrellis.vector.io.json.GeoJsonSupport
import io.circe.generic.JsonCodec

// --- //

@JsonCodec
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
  tileVisibility: Visibility,
  isAOIProject: Boolean,
  aoiCadenceMillis: Long, /* Milliseconds */
  aoisLastChecked: Timestamp,
  tags: List[String] = List.empty,
  extent: Option[Projected[Geometry]] = None,
  manualOrder: Boolean = true
)

/** Case class for project creation */
object Project extends GeoJsonSupport {

  /* One week, in milliseconds */
  val DEFAULT_CADENCE: Long = 604800000

  def tupled = (Project.apply _).tupled

  def create = Create.apply _

  def slugify(input: String): String = {
    import java.text.Normalizer
    Normalizer.normalize(input, Normalizer.Form.NFD)
      .replaceAll("[^\\w\\s-]", ""
        .replace('-', ' ')
        .trim
        .replaceAll("\\s+", "-")
        .toLowerCase)
  }

  @JsonCodec
  case class Create(
    organizationId: UUID,
    name: String,
    description: String,
    visibility: Visibility,
    tileVisibility: Visibility,
    isAOIProject: Boolean,
    aoiCadenceMillis: Long,
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
        tileVisibility,
        isAOIProject,
        aoiCadenceMillis,
        new Timestamp(now.getTime - aoiCadenceMillis),
        tags
      )
    }
  }
}
