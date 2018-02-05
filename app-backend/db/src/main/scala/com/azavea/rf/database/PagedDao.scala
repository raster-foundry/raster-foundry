package com.azavea.rf.database

import com.azavea.rf.database.util._
import com.azavea.rf.datamodel.PaginatedResponse

import doobie._, doobie.implicits._
import cats._, cats.data._, cats.effect.IO, cats.implicits._
import com.lonelyplanet.akka.http.extensions.PageRequest

import scala.concurrent.Future


trait PagedDao[Model] {
  implicit val composite: Composite[Model]
  val selectF: Fragment
  val countF: Fragment

  def filter[M >: Model, T](thing: T)(implicit filterable: Filterable[M, T]): PagedDao.QueryBuilder[Model] =
    PagedDao.QueryBuilder[Model](selectF, countF, filterable.toFilters(thing))

  def list(pageRequest: Option[PageRequest] = None): ConnectionIO[List[Model]] =
    (selectF ++ Page(pageRequest))
      .query[Model]
      .list

  def page(pageRequest: PageRequest)(implicit xa: Transactor[IO]) = {
    val transaction = for {
      page <- list(Some(pageRequest))
      count <- countF.query[Int].unique
    } yield {
      val hasPrevious = pageRequest.offset > 0
      val hasNext = (pageRequest.offset * pageRequest.limit) + 1 < count

      PaginatedResponse[Model](count, hasPrevious, hasNext, pageRequest.offset, pageRequest.limit, page)
    }
    transaction.transact(xa).unsafeToFuture
  }
}

object PagedDao {

  case class QueryBuilder[Model: Composite](selectF: Fragment, countF: Fragment, filters: List[Option[Fragment]]) {

    def filter[M >: Model, T](thing: T)(implicit filterable: Filterable[M, T]): QueryBuilder[Model] =
      this.copy(filters = filters ++ filterable.toFilters(thing))

    def list(pageRequest: Option[PageRequest] = None): ConnectionIO[List[Model]] =
      (selectF ++ Fragments.whereAndOpt(filters: _*) ++ Page(pageRequest))
        .query[Model]
        .list

    def page(
      pageRequest:  PageRequest
    )(implicit xa: Transactor[IO]): Future[PaginatedResponse[Model]] = {
      val transaction = for {
        page <- list(Some(pageRequest))
        count <- (countF ++ Fragments.whereAndOpt(filters: _*)).query[Int].unique
      } yield {
        val hasPrevious = pageRequest.offset > 0
        val hasNext = (pageRequest.offset * pageRequest.limit) + 1 < count

        PaginatedResponse[Model](count, hasPrevious, hasNext, pageRequest.offset, pageRequest.limit, page)
      }
      transaction.transact(xa).unsafeToFuture
    }
  }
}

