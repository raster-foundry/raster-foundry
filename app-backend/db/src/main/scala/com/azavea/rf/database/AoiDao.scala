package com.azavea.rf.database

import java.util.UUID

import cats.implicits._
import com.azavea.rf.database.Implicits._
import com.azavea.rf.database.util._
import com.azavea.rf.datamodel._
import com.lonelyplanet.akka.http.extensions.PageRequest
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._

object AoiDao extends Dao[AOI] {

  val tableName = "aois"

  val selectF: Fragment =
    sql"""
      SELECT
        id, created_at, modified_at,
        created_by, modified_by, owner, shape, filters, is_active, start_time,
        approval_required, project_id
      FROM
    """ ++ tableF

  def unsafeGetAoiById(id: UUID): ConnectionIO[AOI] =
    query.filter(id).select

  def getAoiById(id: UUID): ConnectionIO[Option[AOI]] =
    query.filter(id).selectOption

  def updateAOI(aoi: AOI, user: User): ConnectionIO[Int] = {
    (fr"UPDATE" ++ tableF ++ fr"SET" ++ fr"""
        modified_at = NOW(),
        modified_by = ${user.id},
        shape = ${aoi.shape},
        filters = ${aoi.filters},
        is_active = ${aoi.isActive},
        start_time = ${aoi.startTime},
        approval_required = ${aoi.approvalRequired}
      WHERE
        id = ${aoi.id}
    """).update.run
  }

  def createAOI(aoi: AOI, user: User): ConnectionIO[AOI] = {
    val ownerId = Ownership.checkOwner(user, Some(aoi.owner))

    val aoiCreate: ConnectionIO[AOI] = (fr"INSERT INTO" ++ tableF ++ fr"""
        (id, created_at, modified_at,
        created_by, modified_by, owner, shape, filters, is_active,
        approval_required, start_time, project_id)
      VALUES
        (${aoi.id}, NOW(), NOW(),
        ${user.id}, ${user.id}, ${ownerId}, ${aoi.shape}, ${aoi.filters}, ${aoi.isActive},
        ${aoi.approvalRequired}, ${aoi.startTime}, ${aoi.projectId})
    """).update.withUniqueGeneratedKeys[AOI](
      "id",
      "created_at",
      "modified_at",
      "created_by",
      "modified_by",
      "owner",
      "shape",
      "filters",
      "is_active",
      "start_time",
      "approval_required",
      "project_id"
    )
    aoiCreate
  }

  // TODO embed shape into aoi
  def listAOIs(projectId: UUID,
               page: PageRequest): ConnectionIO[PaginatedResponse[AOI]] =
    query.filter(fr"project_id = ${projectId}").page(page, fr"")

  def listAuthorizedAois(
      user: User,
      aoiQueryParams: AoiQueryParameters,
      page: PageRequest): ConnectionIO[PaginatedResponse[AOI]] = {
    val authedProjectsIO = ProjectDao.authQuery(user, ObjectType.Project).list
    for {
      authedProjects <- authedProjectsIO
      authedProjectIdsF = (authedProjects map { _.id }).toNel map {
        Fragments.in(fr"project_id", _)
      }
      authFilterF = Fragments.orOpt(authedProjectIdsF,
                                    Some(fr"owner = ${user.id}"))
      aois <- {
        AoiDao.query
          .filter(aoiQueryParams)
          .filter(authFilterF)
          .page(page, fr"")
      }
    } yield { aois }
  }

  def deleteAOI(id: UUID): ConnectionIO[Int] = {
    (
      fr"DELETE FROM" ++ tableF ++ Fragments.whereAndOpt(Some(fr"id = ${id}"))
    ).update.run
  }

  def authorize(aoiId: UUID,
                user: User,
                actionType: ActionType): ConnectionIO[Boolean] =
    for {
      aoiO <- AoiDao.query.filter(aoiId).selectOption
      projectAuthed <- aoiO map { _.projectId } match {
        case Some(projectId) =>
          ProjectDao.authorized(user, ObjectType.Project, projectId, actionType)
        case _ => false.pure[ConnectionIO]
      }
    } yield { projectAuthed }
}
