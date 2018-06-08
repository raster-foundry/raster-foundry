package com.azavea.rf.database

import com.azavea.rf.database.Implicits._
import com.azavea.rf.database.util._
import com.azavea.rf.datamodel._

import com.lonelyplanet.akka.http.extensions.PageRequest
import doobie._, doobie.implicits._
import doobie.postgres._, doobie.postgres.implicits._
import cats._, cats.data._, cats.effect.IO, cats.implicits._
import io.circe._
import geotrellis.slick.Projected
import geotrellis.vector.MultiPolygon

import com.lonelyplanet.akka.http.extensions.PageRequest

import scala.concurrent.Future
import java.sql.Timestamp
import java.util.{Date, UUID}


object AoiDao extends Dao[AOI] {

  val tableName = "aois"

  val selectF =
    sql"""
      SELECT
        id, created_at, modified_at,
        created_by, modified_by, owner, area, filters, is_active, start_time,
        approval_required, project_id
      FROM
    """ ++ tableF

  def unsafeGetAoiById(id: UUID): ConnectionIO[AOI] =
    query.filter(id).select

  def getAoiById(id: UUID): ConnectionIO[Option[AOI]] =
    query.filter(id).selectOption

  def updateAOI(aoi: AOI, id: UUID, user: User): ConnectionIO[Int] = {
    (fr"UPDATE" ++ tableF ++ fr"SET" ++ fr"""
        modified_at = NOW(),
        modified_by = ${user.id},
        area = ${aoi.area},
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
        created_by, modified_by, owner, area, filters, is_active,
        approval_required, start_time, project_id)
      VALUES
        (${aoi.id}, NOW(), NOW(),
        ${user.id}, ${user.id}, ${ownerId}, ${aoi.area}, ${aoi.filters}, ${aoi.isActive},
        ${aoi.approvalRequired}, ${aoi.startTime}, ${aoi.projectId})
    """).update.withUniqueGeneratedKeys[AOI](
      "id", "created_at", "modified_at",
      "created_by", "modified_by", "owner", "area", "filters", "is_active",
      "start_time", "approval_required", "project_id"
    )

    aoiCreate
  }

  def listAOIs(projectId: UUID, user: User, page: PageRequest): ConnectionIO[PaginatedResponse[AOI]] =
    query.filter(fr"project_id = ${projectId}").page(page)

  def listAuthorizedAois(user: User, aoiQueryParams: AoiQueryParameters, page: PageRequest): ConnectionIO[PaginatedResponse[AOI]] = {
    val authedProjectsIO = ProjectDao.authQuery(user, ObjectType.Project).list
    for {
      authedProjects <- authedProjectsIO
      authedProjectIdsF = (authedProjects map { _.id }).toNel map {
        Fragments.in(fr"project_id", _)
      }
      authFilterF = Fragments.orOpt(authedProjectIdsF, Some(fr"owner = ${user.id}"))
      aois <- {
        AoiDao.query
          .filter(aoiQueryParams)
          .filter(authFilterF)
          .page(page)
      }
    } yield { aois }
  }

  def deleteAOI(id: UUID, user: User): ConnectionIO[Int]= {
    (
      fr"DELETE FROM" ++ tableF ++ Fragments.whereAndOpt(Some(fr"id = ${id}"))
    ).update.run
  }

  def authorize(aoiId: UUID, user: User, actionType: ActionType): ConnectionIO[Boolean] = for {
    aoiO <- AoiDao.query.filter(aoiId).selectOption
    projectAuthed <- aoiO map { _.projectId } match {
      case Some(projectId) => ProjectDao.query.authorized(user, ObjectType.Project, projectId, actionType)
      case _ => false.pure[ConnectionIO]
    }
  } yield { projectAuthed }
}

