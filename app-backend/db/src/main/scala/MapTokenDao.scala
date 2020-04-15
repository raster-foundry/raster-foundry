package com.rasterfoundry.database

import com.rasterfoundry.datamodel._
import com.rasterfoundry.datamodel.{AuthFailure, PageRequest, Project, ToolRun}

import cats.Applicative
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import doobie.postgres.implicits._

import java.sql.Timestamp
import java.util.UUID

object MapTokenDao extends Dao[MapToken] {

  val tableName = "map_tokens"

  val selectF =
    sql"""
      SELECT
        id, created_at, created_by, modified_at,
        owner, name, project_id, toolrun_id
      FROM
    """ ++ tableF

  def insert(
      newMapToken: MapToken.Create,
      user: User
  ): ConnectionIO[MapToken] = {
    val id = UUID.randomUUID()
    val now = new Timestamp(new java.util.Date().getTime())
    val ownerId = util.Ownership.checkOwner(user, newMapToken.owner)

    sql"""
       INSERT INTO map_tokens
          (id, created_at, created_by, modified_at, owner, name, project_id, toolrun_id)
       VALUES
          (${id}, ${now}, ${user.id}, ${now}, ${ownerId}, ${newMapToken.name},
           ${newMapToken.project}, ${newMapToken.toolRun})
       """.update.withUniqueGeneratedKeys[MapToken](
      "id",
      "created_at",
      "created_by",
      "modified_at",
      "owner",
      "name",
      "project_id",
      "toolrun_id"
    )
  }

  def authorize(
      mapTokenId: UUID,
      user: User,
      actionType: ActionType
  ): ConnectionIO[Boolean] =
    for {
      mapTokenO <- MapTokenDao.query.filter(mapTokenId).selectOption
      projAuthed = (
        mapTokenO flatMap { _.project } map { (projectId: UUID) =>
          {
            ProjectDao.authorized(
              user,
              ObjectType.Project,
              projectId,
              actionType
            )
          }
        }
      ).getOrElse(Applicative[ConnectionIO].pure(AuthFailure[Project]()))
      toolRunAuthed = (
        mapTokenO flatMap { _.toolRun } map { (toolRunId: UUID) =>
          {
            ToolRunDao.authorized(
              user,
              ObjectType.Analysis,
              toolRunId,
              actionType
            )
          }
        }
      ).getOrElse(Applicative[ConnectionIO].pure(AuthFailure[ToolRun]()))
      authTuple <- (projAuthed, toolRunAuthed).tupled
    } yield { authTuple._1.toBoolean || authTuple._2.toBoolean }

  def listAuthorizedMapTokens(
      user: User,
      mapTokenParams: CombinedMapTokenQueryParameters,
      page: PageRequest
  ): ConnectionIO[PaginatedResponse[MapToken]] = {
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
      authFilterF: Fragment = projIdsF orElse analysesIdsF getOrElse Fragment.empty
      mapTokens <- MapTokenDao.query
        .filter(mapTokenParams)
        .filter(authFilterF)
        .page(page)
    } yield { mapTokens }
  }

  def update(mapToken: MapToken, id: UUID): ConnectionIO[Int] = {
    val updateTime = new Timestamp((new java.util.Date()).getTime)
    val idFilter = fr"id = ${id}"

    (sql"""
       UPDATE map_tokens
       SET
         modified_at = ${updateTime},
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
    val ownerId = util.Ownership.checkOwner(user, owner)
    val newMapToken = MapToken.Create(name, project, toolRun, Some(ownerId))
    insert(newMapToken, user)
  }

  def checkProject(
      projectId: UUID
  )(mapToken: UUID): ConnectionIO[Option[MapToken]] =
    query
      .filter(fr"project_id=${projectId}")
      .filter(fr"id=${mapToken}")
      .selectOption

  def checkAnalysis(
      analysisId: UUID
  )(mapToken: UUID): ConnectionIO[Option[MapToken]] =
    query
      .filter(fr"toolrun_id=${analysisId}")
      .filter(fr"id=${mapToken}")
      .selectOption
}
