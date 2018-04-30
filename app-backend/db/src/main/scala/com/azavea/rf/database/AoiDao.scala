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

import scala.concurrent.Future
import java.sql.Timestamp
import java.util.{Date, UUID}


object AoiDao extends Dao[AOI] {

  val tableName = "aois"

  val selectF =
    sql"""
      SELECT
        id, created_at, modified_at, organization_id,
        created_by, modified_by, owner, area, filters, is_active
      FROM
    """ ++ tableF

  def unsafeGetAoiById(id: UUID, user: User): ConnectionIO[AOI] =
    query.ownerFilter(user).filter(id).select

  def getAoiById(id: UUID, user: User): ConnectionIO[Option[AOI]] =
    query.ownerFilter(user).filter(id).selectOption

  def updateAOI(aoi: AOI, id: UUID, user: User): ConnectionIO[Int] = {
    (fr"UPDATE" ++ tableF ++ fr"SET" ++ fr"""
        modified_at = NOW(),
        modified_by = ${user.id},
        area = ${aoi.area},
        filters = ${aoi.filters},
        is_active = ${aoi.isActive}
      WHERE
        id = ${aoi.id}
    """).update.run
  }

  def createAOI(aoi: AOI, projectId: UUID, user: User): ConnectionIO[AOI] = {
    val ownerId = Ownership.checkOwner(user, Some(aoi.owner))

    val aoiCreate: ConnectionIO[AOI] = (fr"INSERT INTO" ++ tableF ++ fr"""
        (id, created_at, modified_at, organization_id,
        created_by, modified_by, owner, area, filters, is_active)
      VALUES
        (${aoi.id}, NOW(), NOW(), ${user.organizationId},
        ${user.id}, ${user.id}, ${ownerId}, ${aoi.area}, ${aoi.filters}, ${aoi.isActive})
    """).update.withUniqueGeneratedKeys[AOI](
      "id", "created_at", "modified_at", "organization_id",
      "created_by", "modified_by", "owner", "area", "filters", "is_active"
    )

    val transaction = for {
      createdAoi <- aoiCreate
      _ <- AoiToProjectDao.create(createdAoi, projectId)
    } yield aoi

    transaction
  }

  def listAOIs(projectId: UUID, user: User, page: PageRequest): ConnectionIO[PaginatedResponse[AOI]] = {
    val joinF =
      fr"INNER JOIN" ++ AoiToProjectDao.tableF ++ fr"a2p" ++
      fr"ON a2p.aoi_id = id" ++ Fragments.whereAndOpt(
        Some(fr"a2p.project_id = ${projectId}")
      )

    query.page(page, selectF ++ joinF, query.countF ++ joinF)
  }

  def deleteAOI(id: UUID, user: User): ConnectionIO[Int]= {
    (
      fr"DELETE FROM" ++ tableF ++ Fragments.whereAndOpt(query.ownerFilterF(user), Some(fr"id = ${id}"))
    ).update.run
  }
}

