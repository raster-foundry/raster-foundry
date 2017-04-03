package com.azavea.rf.database.tables

import java.sql.Timestamp
import java.util.UUID

import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.database.fields.TimestampFields
import com.azavea.rf.datamodel.AOI
import geotrellis.slick.Projected
import geotrellis.vector.MultiPolygon
import com.azavea.rf.database.query._

// --- //

/** The table description for the "aois" table. */
class AOIs(_tableTag: Tag) extends Table[AOI](_tableTag, "aois") with TimestampFields {

  def * = (id, createdAt, modifiedAt, area) <> (AOI.tupled, AOI.unapply)

  /* Database Fields */
  val id: Rep[UUID] = column[java.util.UUID]("id", O.PrimaryKey)
  val createdAt: Rep[Timestamp] = column[java.sql.Timestamp]("created_at")
  val modifiedAt: Rep[Timestamp] = column[java.sql.Timestamp]("modified_at")
//  val organizationId: Rep[UUID] = column[java.util.UUID]("organization_id")
//  val createdBy: Rep[String] = column[String]("created_by", O.Length(255,varying=true))
//  val modifiedBy: Rep[String] = column[String]("modified_by", O.Length(255,varying=true))

  /* Unique Fields */
  val area: Rep[Projected[MultiPolygon]] = column[Projected[MultiPolygon]]("area")

  /* Foreign Keys */
//  lazy val organizationsFk = foreignKey("projects_organization_id_fkey", organizationId, Organizations)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)

  /* TODO 2017 April  3 @ 16:19
   * Are these needed?
   */
//  lazy val createdByUserFK = foreignKey("projects_created_by_fkey", createdBy, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
//  lazy val modifiedByUserFK = foreignKey("projects_modified_by_fkey", modifiedBy, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)

}
