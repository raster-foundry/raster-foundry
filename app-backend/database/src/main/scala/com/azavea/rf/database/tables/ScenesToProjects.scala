package com.azavea.rf.database.tables

import com.azavea.rf.database.fields._
import com.azavea.rf.database.query._
import com.azavea.rf.database.sort._
import com.azavea.rf.database.{Database => DB}
import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.datamodel._

import com.typesafe.scalalogging.LazyLogging
import com.lonelyplanet.akka.http.extensions.PageRequest
import spray.json._

import java.util.UUID
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/** Table description of table scenes_to_project. Objects of this class serve as prototypes for rows in queries. */
class ScenesToProjects(_tableTag: Tag) extends Table[SceneToProject](_tableTag, "scenes_to_projects") {
  def * = (sceneId, projectId, sceneOrder, mosaicDefinition) <> (SceneToProject.tupled, SceneToProject.unapply)

  val sceneId: Rep[java.util.UUID] = column[java.util.UUID]("scene_id")
  val projectId: Rep[java.util.UUID] = column[java.util.UUID]("project_id")
  val sceneOrder: Rep[Option[Int]] = column[Option[Int]]("scene_order")
  val mosaicDefinition: Rep[MosaicDefinition] = column[MosaicDefinition]("mosaic_definition")

  val pk = primaryKey("scenes_to_projects_pkey", (sceneId, projectId))

  lazy val projectsFk = foreignKey("scenes_to_projects_project_id_fkey", projectId, Projects)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.Cascade)
  lazy val scenesFk = foreignKey("scenes_to_projects_scene_id_fkey", sceneId, Scenes)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.Cascade)
}

/** Collection-like TableQuery object for table ScenesToProjects */
object ScenesToProjects extends TableQuery(tag => new ScenesToProjects(tag)) with LazyLogging {

  /** Update a scene's location in a given project's ordering */
  def setS2POrder(projectId: UUID, ordering: Seq[UUID])(implicit database: DB) = {
    val toOrderS2P =
      for {
        s2p <- ScenesToProjects
          if s2p.projectId === projectId.bind
          if s2p.sceneId inSetBind ordering
      } yield s2p

    val setUnorderedS2P =
      (for {
        s2p <- ScenesToProjects
          if s2p.projectId === projectId.bind
          if !(s2p.sceneId inSetBind ordering)
      } yield s2p.sceneOrder).update(None)

    val setOrderedS2P = DBIO.sequence(ordering.zipWithIndex map { case (sceneId, idx) =>
      toOrderS2P.filter(_.sceneId === sceneId).map(_.sceneOrder).update(Some(idx))
    })

    database.db.run {
      setUnorderedS2P andThen setOrderedS2P
    }
  }

  def listS2POrder(projectId: UUID)(implicit database: DB) = database.db.run {
    ScenesToProjects
      .filter(_.projectId === projectId)
      .sortBy(_.sceneOrder.asc.nullsLast)
      .map(_.sceneId)
      .result
  }

}

























