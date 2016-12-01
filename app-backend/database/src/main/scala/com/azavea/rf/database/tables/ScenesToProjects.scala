package com.azavea.rf.database.tables

import com.azavea.rf.database.{Database => DB}
import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.datamodel._

/** Table description of table scenes_to_project. Objects of this class serve as prototypes for rows in queries. */
class ScenesToProjects(_tableTag: Tag) extends Table[SceneToProject](_tableTag, "scenes_to_projects") {
  def * = (sceneId, projectId) <> (SceneToProject.tupled, SceneToProject.unapply)

  val sceneId: Rep[java.util.UUID] = column[java.util.UUID]("scene_id")
  val projectId: Rep[java.util.UUID] = column[java.util.UUID]("project_id")

  val pk = primaryKey("scenes_to_projects_pkey", (sceneId, projectId))

  lazy val projectsFk = foreignKey("scenes_to_projects_project_id_fkey", projectId, Projects)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.Cascade)
  lazy val scenesFk = foreignKey("scenes_to_projects_scene_id_fkey", sceneId, Scenes)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.Cascade)
}

/** Collection-like TableQuery object for table ScenesToProjects */
object ScenesToProjects extends TableQuery(tag => new ScenesToProjects(tag))
