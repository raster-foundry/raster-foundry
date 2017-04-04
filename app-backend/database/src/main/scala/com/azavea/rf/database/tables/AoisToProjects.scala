package com.azavea.rf.database.tables

import java.util.UUID

import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.datamodel._
import com.typesafe.scalalogging.LazyLogging

// --- //

/** The table description for the "aois_to_projects" many-to-many table. */
class AoisToProjects(_tableTag: Tag) extends Table[AoiToProject](_tableTag, "aois_to_projects") {
  def * = (aoiId, projectId) <> (AoiToProject.tupled, AoiToProject.unapply)

  val aoiId: Rep[UUID] = column[UUID]("aoi_id", O.PrimaryKey)
  val projectId: Rep[UUID] = column[UUID]("project_id", O.PrimaryKey)

  val pk = primaryKey("aois_to_projects_pkey", (aoiId, projectId))

  lazy val projectsFk = foreignKey("aoi_to_projects_project_id_fkey", projectId, Projects)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.Cascade)
  lazy val aoiFk = foreignKey("aoi_to_projects_aoi_id_fkey", aoiId, Scenes)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.Cascade)

}

object AoisToProjects extends TableQuery(tag => new AoisToProjects(tag)) with LazyLogging
