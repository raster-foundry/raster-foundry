package com.azavea.rf.database

import com.azavea.rf.database.Implicits._
import com.azavea.rf.datamodel._
import com.lonelyplanet.akka.http.extensions.PageRequest
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import cats._
import cats.data._
import cats.effect.IO
import cats.implicits._
import java.sql.Timestamp
import java.util.{Date, UUID}

import scala.concurrent.Future

object MapTokenDao extends Dao[MapToken] {

  val tableName = "map_tokens"

  val selectF =
    sql"""
      SELECT
        id, created_at, created_by, modified_at, modified_by,
        owner, name, project_id, toolrun_id
      FROM
    """ ++ tableF

  def insert(newMapToken: MapToken.Create,
             user: User): ConnectionIO[MapToken] = {
    val id = UUID.randomUUID()
    val now = new Timestamp(new java.util.Date().getTime())
    val ownerId = util.Ownership.checkOwner(user, newMapToken.owner)

    sql"""
       INSERT INTO map_tokens
          (id, created_at, created_by, modified_at, modified_by, owner, name, project_id, toolrun_id)
       VALUES
          (${id}, ${now}, ${user.id}, ${now}, ${user.id}, ${ownerId}, ${newMapToken.name},
           ${newMapToken.project}, ${newMapToken.toolRun})
       """.update.withUniqueGeneratedKeys[MapToken](
      "id",
      "created_at",
      "created_by",
      "modified_at",
      "modified_by",
      "owner",
      "name",
      "project_id",
      "toolrun_id"
    )
  }

  def authorize(mapTokenId: UUID,
                user: User,
                actionType: ActionType): ConnectionIO[Boolean] =
    for {
      mapTokenO <- MapTokenDao.query.filter(mapTokenId).selectOption
      projAuthed = (
        mapTokenO flatMap { _.project } map { (projectId: UUID) =>
          {
            ProjectDao.authorized(user,
                                  ObjectType.Project,
                                  projectId,
                                  actionType)
          }
        }
      ).getOrElse(false.pure[ConnectionIO])
      toolRunAuthed = (
        mapTokenO flatMap { _.toolRun } map { (toolRunId: UUID) =>
          {
            ToolRunDao.authorized(user,
                                  ObjectType.Analysis,
                                  toolRunId,
                                  actionType)
          }
        }
      ).getOrElse(false.pure[ConnectionIO])
      authTuple <- (projAuthed, toolRunAuthed).tupled
    } yield { authTuple._1 || authTuple._2 }

  def listAuthorizedMapTokens(
      user: User,
      mapTokenParams: CombinedMapTokenQueryParameters,
      page: PageRequest): ConnectionIO[PaginatedResponse[MapToken]] = {
    val authedProjectsIO = ProjectDao.authQuery(user, ObjectType.Project).list
    val authedAnalysesIO = ToolRunDao.authQuery(user, ObjectType.Analysis).list

    for {
      projAndAnalyses <- (authedProjectsIO, authedAnalysesIO).tupled
      (authedProjects, authedAnalyses) = projAndAnalyses
      projIdsF: Option[Fragment] = (authedProjects map { _.id }).toNel map {
        Fragments.in(fr"project_id", _)
      }
      analysesIdsF: Option[Fragment] = (authedAnalyses map { _.id }).toNel map {
        Fragments.in(fr"toolrun_id", _)
      }
      authFilterF: Fragment = Fragments.orOpt(projIdsF,
                                              analysesIdsF,
                                              Some(fr"owner = ${user.id}"))
      mapTokens <- {
        MapTokenDao.query
          .filter(mapTokenParams)
          .filter(authFilterF)
          .page(page, fr"")
      }
    } yield { mapTokens }
  }

  def update(mapToken: MapToken, id: UUID, user: User): ConnectionIO[Int] = {
    val updateTime = new Timestamp((new java.util.Date()).getTime)
    val idFilter = fr"id = ${id}"

    (sql"""
       UPDATE map_tokens
       SET
         modified_at = ${updateTime},
         modified_by = ${user.id},
         owner = ${mapToken.owner},
         name = ${mapToken.name},
         project_id = ${mapToken.project},
         toolrun_id = ${mapToken.toolRun}
       """ ++ Fragments.whereAndOpt(Some(idFilter))).update.run
  }

  def create(
      user: User,
      owner: Option[String],
      name: String,
      project: Option[UUID],
      toolRun: Option[UUID]
  ): ConnectionIO[MapToken] = {
    val id = UUID.randomUUID
    val now = new Timestamp((new java.util.Date()).getTime())
    val ownerId = util.Ownership.checkOwner(user, owner)
    val newMapToken = MapToken.Create(name, project, toolRun, Some(ownerId))
    insert(newMapToken, user)
  }
}
