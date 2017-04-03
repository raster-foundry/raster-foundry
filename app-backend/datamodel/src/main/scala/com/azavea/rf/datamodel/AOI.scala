package com.azavea.rf.datamodel

import java.sql.Timestamp
import java.util.UUID

import geotrellis.vector.MultiPolygon

// --- //

/** A Project's Area of Interest.
  * This represents an area on the map (a `MultiPolygon`) which a user has
  * set filters for.  If a new Scene entering the system passes these filters,
  * the Scene will be added to the user's Project in a "pending" state. If the
  * user then accepts a "pending" Scene, it will be added to their project.
  */
case class AOI(
  /* Database fields */
  id: UUID,
  createdAt: Timestamp,
  modifiedAt: Timestamp,
//  organizationId: UUID,
//  createdBy: String,
//  modifiedBy: String,

  /* Unique fields */
  area: MultiPolygon
)

/* TODO 2017 April  3 @ 15:38
 * I'm not sure if this needs `GeoJsonSupport`. Why does `Project`?
 */
object AOI {

  def tupled = (AOI.apply _).tupled

  def create = Create.apply _

  case class Create(area: MultiPolygon) {
    def toAOI(userId: String): AOI = {
      val now = new Timestamp((new java.util.Date()).getTime)

      AOI(UUID.randomUUID, now, now, area)
    }
  }
}
