package com.rasterfoundry.database

import com.rasterfoundry.common.ast._
import com.rasterfoundry.common.ast.codec.MapAlgebraCodec._
import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.database.util._
import com.rasterfoundry.datamodel.{
  ActionType,
  AuthResult,
  GroupType,
  ObjectType,
  PaginatedResponse,
  ToolRun,
  ToolRunWithRelated,
  User
}
import com.rasterfoundry.datamodel.{Order, PageRequest}

import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import doobie.postgres.circe.jsonb.implicits._
import doobie.postgres.implicits._

import java.sql.Timestamp
import java.util.UUID

@SuppressWarnings(Array("EmptyCaseClass"))
final case class ToolRunDao()

object ToolRunDao extends Dao[ToolRun] with ObjectPermissions[ToolRun] {

  val tableName = "tool_runs"

  val selectF = sql"""
    SELECT
      id, name, created_at, created_by, modified_at, owner, visibility,
      project_id, project_layer_id, template_id, execution_parameters
    FROM
  """ ++ tableF

  def unsafeGetToolRunById(toolRunId: UUID): ConnectionIO[ToolRun] =
    query.filter(toolRunId).select

  def insertToolRun(
      newRun: ToolRun.Create,
      user: User
  ): ConnectionIO[ToolRun] = {
    val now = new Timestamp(new java.util.Date().getTime())
    val id = UUID.randomUUID()

    sql"""
          INSERT INTO tool_runs
            (id, name, created_at, created_by, modified_at, owner, visibility,
             execution_parameters, project_id, project_layer_id, template_id)
          VALUES
            (${id}, ${newRun.name}, ${now}, ${user.id}, ${now}, ${newRun.owner
      .getOrElse(user.id)}, ${newRun.visibility}, ${newRun.executionParameters},
             ${newRun.projectId}, ${newRun.projectLayerId}, ${newRun.templateId})
       """.update.withUniqueGeneratedKeys[ToolRun](
      "id",
      "name",
      "created_at",
      "created_by",
      "modified_at",
      "owner",
      "visibility",
      "project_id",
      "project_layer_id",
      "template_id",
      "execution_parameters"
    )
  }

  def updateToolRun(
      updatedRun: ToolRun,
      id: UUID
  ): ConnectionIO[Int] = {
    val now = new Timestamp(new java.util.Date().getTime())
    val idFilter = fr"id = ${id}"

    (sql"""
       UPDATE tool_runs
       SET
         name = ${updatedRun.name},
         modified_at = ${now},
         visibility = ${updatedRun.visibility},
         execution_parameters = ${updatedRun.executionParameters}
       """ ++ Fragments.whereAndOpt(Some(idFilter))).update.run
  }

  def getToolRun(toolRunId: UUID): ConnectionIO[Option[ToolRun]] =
    query.filter(toolRunId).selectOption

  def authQuery(
      user: User,
      objectType: ObjectType,
      ownershipTypeO: Option[String] = None,
      groupTypeO: Option[GroupType] = None,
      groupIdO: Option[UUID] = None
  ): Dao.QueryBuilder[ToolRun] =
    user.isSuperuser match {
      case true =>
        Dao.QueryBuilder[ToolRun](selectF, tableF, List.empty)
      case false =>
        Dao.QueryBuilder[ToolRun](
          selectF,
          tableF,
          List(
            queryObjectsF(
              user,
              objectType,
              ActionType.View,
              ownershipTypeO,
              groupTypeO,
              groupIdO
            )
          )
        )
    }

  def authorized(
      user: User,
      objectType: ObjectType,
      objectId: UUID,
      actionType: ActionType
  ): ConnectionIO[AuthResult[ToolRun]] =
    this.query
      .filter(authorizedF(user, objectType, actionType))
      .filter(objectId)
      .selectOption
      .map(AuthResult.fromOption _)

  def authorizeReferencedProject(
      user: User,
      toolRunId: UUID,
      projectId: UUID
  ): ConnectionIO[Boolean] =
    for {
      toolRunAuthorized <- this.authorized(
        user,
        ObjectType.Analysis,
        toolRunId,
        ActionType.View
      )
      toolRun <- this.query
        .filter(toolRunId)
        .select
      toolRunOwner <- UserDao.unsafeGetUserById(toolRun.owner)
      ownerProjectAuthorization <- ProjectDao.authorized(
        toolRunOwner,
        ObjectType.Project,
        projectId,
        ActionType.View
      )
      ast = toolRun.executionParameters.as[MapAlgebraAST] match {
        case Left(e)                             => throw e
        case Right(mapAlgebraAST: MapAlgebraAST) => mapAlgebraAST
      }
      projectIds = ast.sources.collect {
        case MapAlgebraAST.ProjectRaster(_, projId, _, _, _) => projId
      }
      result <- (toolRunAuthorized.toBoolean && ownerProjectAuthorization.toBoolean && projectIds
        .contains(projectId))
        .pure[ConnectionIO]
    } yield result

  /** List analyses with related information from project layers
    *
    * user is optional because it's possible to invoke this from map token or public
    * authorization, in which case we don't have a user for ownership filtering
    */
  def listAnalysesWithRelated(
      user: Option[User],
      pageRequest: PageRequest,
      projectId: UUID,
      layerIdO: Option[UUID] = None,
      ownershipTypeO: Option[String] = None,
      groupTypeO: Option[GroupType] = None,
      groupIdO: Option[UUID] = None
  ): ConnectionIO[PaginatedResponse[ToolRunWithRelated]] = {
    val selectF: Fragment = fr"""
        SELECT tr.id, tr.name, tr.created_at, tr.created_by, tr.modified_at,
          tr.owner, tr.visibility, tr.project_id, tr.project_layer_id,
          tr.template_id, tr.execution_parameters, t.title template_title,
          pl.color_group_hex layer_color_group_hex, pl.geometry layer_geometry
      """
    val fromF: Fragment = fr"""
        FROM tool_runs tr
        JOIN tools t on t.id = tr.template_id
        JOIN project_layers pl on pl.id = tr.project_layer_id
      """
    val filters: List[Option[Fragment]] = List(
      Some(fr"tr.project_id = ${projectId}"),
      layerIdO.map(layerId => fr"tr.project_layer_id = ${layerId}"),
      user flatMap {
        queryObjectsF(
          _,
          ObjectType.Analysis,
          ActionType.View,
          ownershipTypeO,
          groupTypeO,
          groupIdO,
          Some("tr")
        )
      }
    )
    val countF: Fragment = fr"SELECT count(tr.id)" ++ fromF

    for {
      page <- (selectF ++ fromF ++ Fragments.whereAndOpt(filters: _*) ++ Page(
        pageRequest.copy(
          sort = pageRequest.sort ++ Map(
            "tr.modified_at" -> Order.Desc,
            "tr.id" -> Order.Desc
          )
        )
      )).query[ToolRunWithRelated]
        .to[List]
      count <- (countF ++ Fragments.whereAndOpt(filters: _*))
        .query[Int]
        .unique
    } yield {
      val hasPrevious = pageRequest.offset > 0
      val hasNext = (pageRequest.offset * pageRequest.limit) + 1 < count

      PaginatedResponse[ToolRunWithRelated](
        count,
        hasPrevious,
        hasNext,
        pageRequest.offset,
        pageRequest.limit,
        page
      )
    }
  }

  def analysisReferencesProject(
      analysisId: UUID,
      projectId: UUID
  ): ConnectionIO[Boolean] =
    query.filter(fr"project_id = $projectId").filter(analysisId).exists
}
