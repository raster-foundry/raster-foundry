package com.azavea.rf.database.tables

import java.util.UUID

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import cats.implicits._
import com.azavea.rf.database.{Database => DB}
import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.datamodel._
import com.lonelyplanet.akka.http.extensions.PageRequest
import com.typesafe.scalalogging.LazyLogging
import geotrellis.slick.Projected
import geotrellis.vector.Polygon

// --- //

/** Table description of table scenes_to_project. Objects of this class serve as prototypes for rows in queries. */
class ScenesToProjects(_tableTag: Tag) extends Table[SceneToProject](_tableTag, "scenes_to_projects") {
  def * = (sceneId, projectId, accepted, sceneOrder, colorCorrectParams) <> (SceneToProject.tupled, SceneToProject.unapply)

  val sceneId: Rep[UUID] = column[UUID]("scene_id", O.PrimaryKey)
  val projectId: Rep[UUID] = column[UUID]("project_id", O.PrimaryKey)
  val accepted: Rep[Boolean] = column[Boolean]("accepted")
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
    val sceneToProject = SceneToProject(sceneId, projectId, true, None, Some(colorCorrectParams))
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

  def setColorCorrectParamsBatch(projectId: UUID, params: BatchParams)(implicit database:DB) ={
    val scenesToProject = params.items
      .map(p => SceneToProject(p.sceneId, projectId, true, None, Some(p.params)))
      .map(s => ScenesToProjects.insertOrUpdate(s))

    val recordCheck = params.items
      .map(p =>
        ScenesToProjects
          .filter(_.projectId === projectId)
          .filter(_.sceneId === p.sceneId)
          .length
          .result
          .map {
            case 1 => p
            case _ => throw new IllegalStateException("Error updating scene's color correction")
          })

    val action = (for {
      dbUpsert <- DBIO.seq(scenesToProject.map(o => o): _*)
      dbCheck <- DBIO.seq(recordCheck.map(o => o): _*)
    } yield (dbUpsert, dbCheck)).transactionally

    database.db.run(action).map {
      case (_, _) => scenesToProject
      case _ => throw new IllegalStateException("One or more scenes did not update properly")
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

  def getMosaicDefinition(projectId: UUID, polygonOption: Option[Projected[Polygon]])(implicit database: DB): Future[Option[Seq[MosaicDefinition]]] = {
    val query = for {
      (s2p, s) <- ScenesToProjects join Scenes.filterByTileFootprint(polygonOption) on (_.sceneId === _.id)
    } yield {
      s2p
    }

    database.db.run {
      query
        .filter(_.projectId === projectId)
        .sortBy(_.sceneOrder.asc.nullsLast)
        .result
    } map { s2p =>
      if (s2p.length > 0) {
        Some(s2p.map { sceneToProject =>
          MosaicDefinition(sceneToProject.sceneId, sceneToProject.colorCorrectParams)
        })
      } else {
        None
      }
    }
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

  /** Approve a pending Scene which passed an AOI check. */
  def acceptScene(projectId: UUID, sceneId: UUID)(implicit database: DB): Future[Int] = {
    database.db.run(
      ScenesToProjects
        .filter(r => r.projectId === projectId && r.sceneId === sceneId)
        .map(_.accepted)
        .update(true)
    )
  }

  /** All ingest locations for Scenes in a given Project.
    * If even one of the Scenes doesn't have an `ingestLocation`, we fail
    * the whole operation.
    */
  def allSceneIngestLocs(projectId: UUID)(implicit database: DB): Future[Option[List[(UUID, String)]]] = {
    val sceneIds: Future[Seq[UUID]] = database.db.run {
      ScenesToProjects
        .filter(_.projectId === projectId)
        .sortBy(_.sceneOrder.asc.nullsLast)
        .map(_.sceneId)
        .result
    }

    val scenes: Seq[UUID] => Future[Option[List[(UUID, String)]]] = { ids: Seq[UUID] =>
      database.db.run(Scenes.filter(_.id inSet ids).map(s => s.ingestLocation.map((s.id, _))).result)
        .map(_.toList.sequence)
    }

    sceneIds.flatMap(scenes)
  }

  def allScenes(projectId: UUID)(implicit database: DB): Future[Seq[UUID]] = database.db.run {
    ScenesToProjects
      .filter(_.projectId === projectId)
      .sortBy(_.sceneOrder.asc.nullsLast)
      .map(_.sceneId)
      .result
  }
}
