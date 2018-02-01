package com.azavea.rf.database

import com.azavea.rf.database.meta.RFMeta._
import com.azavea.rf.database.util._
import com.azavea.rf.datamodel._

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


object AoiDao extends Dao[AOI]("aois") {

  val selectF =
    sql"""
      SELECT
        id, created_at, created_by, modified_at, modified_by,
        organization_id, owner, area, filters
      FROM
    """ ++ tableF

  def select(id: UUID) =
    (selectF ++ fr"WHERE id = $id").query[AOI].unique

  def listFilters(params: AoiQueryParameters, user: User) =
    Filters.organization(params.orgParams) ++
    Filters.user(params.userParams) ++
    Filters.timestamp(params.timestampParams) ++
    Filters.filterToSharedIfNotInRoot(user)

  def list(
    params: AoiQueryParameters,
    user: User,
    pageRequest: Option[PageRequest]
  ): ConnectionIO[List[AOI]] = {
    val filters: List[Option[Fragment]] = listFilters(params, user)
    list(filters, pageRequest)
  }

  def page(
    params: AoiQueryParameters,
    user: User,
    pageRequest: PageRequest
  )(implicit xa: Transactor[IO]): Future[PaginatedResponse[AOI]] = {
    val filters: List[Option[Fragment]] = listFilters(params, user)
    page(filters, pageRequest)
  }

  def create(
    user: User,
    owner: Option[String],
    organizationId: UUID,
    area: Projected[MultiPolygon],
    filters: Json
  ): ConnectionIO[AOI] = {
    val id = UUID.randomUUID
    val now = new Timestamp((new java.util.Date()).getTime())
    val ownerId = Ownership.checkOwner(user, owner)
    val userId = user.id
    (fr"INSERT INTO" ++ tableF ++ fr"""
        (id, created_at, created_by, modified_at, modified_by,
        organization_id, owner, area, filters)
      VALUES
        ($id, $now, $userId, $now, $userId,
        $organizationId, $ownerId, $area, $filters)
    """).update.withUniqueGeneratedKeys[AOI](
      "id", "created_at", "created_by", "modified_at", "modified_by",
      "organization_id", "owner", "area", "filters"
    )
  }
}

object AoiJson {
  import io.circe._
  import scala.concurrent.Future
  // Potential strategy for replacement of `AOI.Create`
  def create(
    aoiJson: Json,
    user: User
  )(implicit xa: Transactor[IO]): Either[DecodingFailure, Future[AOI]] = {
    val c = aoiJson.hcursor
    (c.get[Option[String]]("owner"),
     c.get[UUID]("organizationId"),
     c.get[Projected[MultiPolygon]]("area"),
     c.get[Json]("filters"))
       .mapN({ case (owner, organizationId, area, filters) =>
         val creation = AoiDao.create(user, owner, organizationId, area, filters)
         creation.transact(xa).unsafeToFuture()
       })
  }
}

