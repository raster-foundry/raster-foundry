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
  def * = (sceneId, projectId, sceneOrder, colorCorrectParams) <> (SceneToProject.tupled, SceneToProject.unapply)

  val sceneId: Rep[java.util.UUID] = column[java.util.UUID]("scene_id", O.PrimaryKey)
  val projectId: Rep[java.util.UUID] = column[java.util.UUID]("project_id", O.PrimaryKey)
  val sceneOrder: Rep[Option[Int]] = column[Option[Int]]("scene_order")
  val colorCorrectParams: Rep[Option[ColorCorrect.Params]] = column[Option[ColorCorrect.Params]]("mosaic_definition")

  val pk = primaryKey("scenes_to_projects_pkey", (sceneId, projectId))

  lazy val projectsFk = foreignKey("scenes_to_projects_project_id_fkey", projectId, Projects)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.Cascade)
  lazy val scenesFk = foreignKey("scenes_to_projects_scene_id_fkey", sceneId, Scenes)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.Cascade)
}

/** Collection-like TableQuery object for table ScenesToProjects */
object ScenesToProjects extends TableQuery(tag => new ScenesToProjects(tag)) with LazyLogging {

  /** Update a scene's location in a given project's ordering */
  def setManualOrder(projectId: UUID, ordering: Seq[UUID])(implicit database: DB) = {
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
      (for {
        _ <- setUnorderedS2P
        set <- setOrderedS2P
      } yield set).transactionally
    }
  }

  /** List the scenes as they are ordered for a given project */
  def listManualOrder(projectId: UUID, pageRequest: PageRequest)(implicit database: DB) = {
    val lengthQuery = ScenesToProjects
      .filter(_.projectId === projectId)
      .length
      .result

    val s2pQuery = ScenesToProjects
      .filter(_.projectId === projectId)
      .sortBy(_.sceneOrder.asc.nullsLast)
      .map(_.sceneId)
      .drop(pageRequest.offset * pageRequest.limit)
      .take(pageRequest.limit)
      .result

    val transaction = (for {
      length <- lengthQuery
      s2p <- s2pQuery
    } yield (s2p, length)).transactionally

    database.db.run {
      transaction
    }.map { case (s2p, length) =>
      val hasNext = (pageRequest.offset + 1) * pageRequest.limit < length
      val hasPrevious = pageRequest.offset > 0
      PaginatedResponse[UUID](
        length,
        hasPrevious,
        hasNext,
        pageRequest.offset,
        pageRequest.limit,
        s2p
      )
    }
  }

  /** Attach color correction parameters to a project/scene pairing */
  def setColorCorrectParams(projectId: UUID, sceneId: UUID, colorCorrectParams: ColorCorrect.Params)(implicit database: DB) = {
    val sceneToProject = SceneToProject(sceneId, projectId, None, Some(colorCorrectParams))
    database.db.run {
      ScenesToProjects.insertOrUpdate(
        sceneToProject
      )
    }.map {
      case 1 => sceneToProject
      case count => throw new IllegalStateException(
        s"Error updating scene's color correction: update result expected to be 1, was $count"
      )
    }
  }



  /** Get color correction parameters from a project/scene pairing */
  def getColorCorrectParams(projectId: UUID, sceneId: UUID)(implicit database: DB) = {
    database.db.run {
      ScenesToProjects
        .filter({ s2p => s2p.projectId === projectId && s2p.sceneId === sceneId })
        .map(_.colorCorrectParams)
        .result
    }.map(_.headOption)
  }

  /** Get the complete mosaic definition for a giving project */
  def getMosaicDefinition(projectId: UUID)(implicit database: DB): Future[Option[Seq[MosaicDefinition]]] = {
    database.db.run {
      ScenesToProjects
        .filter(_.projectId === projectId)
        .sortBy(_.sceneOrder.asc.nullsLast)
        .result
    }.map({ s2p =>
      if (s2p.length > 0)
        Some(s2p.map { sceneToProject =>
          MosaicDefinition(sceneToProject.sceneId, sceneToProject.colorCorrectParams)
        })
      else
        None
    })
  }
}
