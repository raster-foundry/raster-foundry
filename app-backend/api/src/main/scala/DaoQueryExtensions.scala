package com.azavea.rf.api

import com.azavea.rf.database.meta.RFMeta._
import com.azavea.rf.database.util._
import com.azavea.rf.datamodel._
import com.azavea.rf.database.Dao

import doobie._, doobie.implicits._
import doobie.postgres._, doobie.postgres.implicits._
import cats._, cats.data._, cats.effect.IO, cats.implicits._
import io.circe._
import geotrellis.slick.Projected
import geotrellis.vector.MultiPolygon
import com.lonelyplanet.akka.http.extensions._
import doobie._

import scala.concurrent.Future
import java.sql.Timestamp
import java.util.{Date, UUID}


trait DaoQueryExtension[Model, QueryParams] {
  def dao: Dao[Model]

  // The method which constructs (optional) doobie fragments on the basis of (optional) query parameters
  def listFilters(params: QueryParams, user: User): List[Option[Fragment]]

  def list(params: QueryParams, user: User, page: Option[PageRequest]): ConnectionIO[List[Model]] =
    dao.list(listFilters(params, user), page)

  def page(params: QueryParams, user: User, page: PageRequest)(implicit xa: Transactor[IO]): Future[PaginatedResponse[Model]] =
    dao.page(listFilters(params, user), page)
}

object DaoQueryExtension {
  implicit class AoiQueryExtension(val dao: Dao[AOI]) extends DaoQueryExtension[AOI, AoiQueryParameters] {
    def listFilters(params: AoiQueryParameters, user: User) =
      Filters.organization(params.orgParams) ++
      Filters.user(params.userParams) ++
      Filters.timestamp(params.timestampParams) ++
      Filters.filterToSharedIfNotInRoot(user)
  }
}


