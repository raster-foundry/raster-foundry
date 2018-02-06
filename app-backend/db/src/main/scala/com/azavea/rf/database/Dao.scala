package com.azavea.rf.database

import com.azavea.rf.database.meta.RFMeta._
import com.azavea.rf.database.util._
import com.azavea.rf.datamodel._

import doobie._, doobie.implicits._
import doobie.postgres._, doobie.postgres.implicits._
import cats._, cats.data._, cats.effect.IO, cats.implicits._
import com.lonelyplanet.akka.http.extensions.PageRequest

import scala.concurrent.Future
import java.util.UUID


/**
 * This is abstraction over the listing of arbitrary types from the DB with filters/pagination
 */
trait Dao[Model] {

  implicit val composite: Composite[Model] = implicitly[Composite[Model]]

  val tableName: String

  /** The fragment which holds the associated table's name */
  val tableF = Fragment.const(tableName)

  /** An abstract select statement to be used for constructing queries */
  def selectF: Fragment

  /** A fragment which holds count SQL */
  val countF = (sql"SELECT count(*) FROM" ++ tableF)

  /** Begin construction of a complex, filtered query */
  def query: Dao.QueryBuilder[Model] = Dao.QueryBuilder[Model](selectF, countF, List.empty)
}

object Dao {

  case class QueryBuilder[Model: Composite](selectF: Fragment, countF: Fragment, filters: List[Option[Fragment]]) {

    /** Add another filter to the query being constructed */
    def filter[M >: Model, T](thing: T)(implicit filterable: Filterable[M, T]): QueryBuilder[Model] =
      this.copy(filters = filters ++ filterable.toFilters(thing))

    /** Provide a list of responses within the PaginatedResponse wrapper */
    def page(pageRequest:  PageRequest)(implicit xa: Transactor[IO]): Future[PaginatedResponse[Model]] = {
      val transaction = for {
        page <- (selectF ++ Fragments.whereAndOpt(filters: _*) ++ Page(pageRequest)).query[Model].list
        count <- (countF ++ Fragments.whereAndOpt(filters: _*)).query[Int].unique
      } yield {
        val hasPrevious = pageRequest.offset > 0
        val hasNext = (pageRequest.offset * pageRequest.limit) + 1 < count

        PaginatedResponse[Model](count, hasPrevious, hasNext, pageRequest.offset, pageRequest.limit, page)
      }

      transaction.transact(xa).unsafeToFuture
    }

    /** Provide a list of responses */
    def list(pageRequest: PageRequest)(implicit xa: Transactor[IO]): Future[List[Model]] = {
      val query = (selectF ++ Fragments.whereAndOpt(filters: _*) ++ Page(Some(pageRequest)))
        .query[Model]
        .list

      query.transact(xa).unsafeToFuture
    }

    /** Provide a list of responses */
    def list(limit: Int)(implicit xa: Transactor[IO]): Future[List[Model]] = {
      val query = (selectF ++ Fragments.whereAndOpt(filters: _*) ++ fr"LIMIT $limit")
        .query[Model]
        .list

      query.transact(xa).unsafeToFuture
    }

    /** Provide a list of responses */
    def list(offset: Int, limit: Int)(implicit xa: Transactor[IO]): Future[List[Model]] = {
      val query = (selectF ++ Fragments.whereAndOpt(filters: _*) ++ fr"OFFSET $offset" ++ fr"LIMIT $limit")
        .query[Model]
        .list

      query.transact(xa).unsafeToFuture
    }

    /** Select a single value - throw on failure */
    def select(id: Option[UUID])(implicit xa: Transactor[IO]): Future[Model] = {
      val updatedFilters = filters :+ id.map({ identity => fr"id = $id"})
      val query = (selectF ++ Fragments.whereAndOpt(updatedFilters: _*)).query[Model].unique
      query.transact(xa).unsafeToFuture
    }

    /** Select a single value - throw on failure */
    def select(id: UUID)(implicit xa: Transactor[IO]): Future[Model] = select(Some(id))

    /** Select a single value - throw on failure */
    def select(implicit xa: Transactor[IO]): Future[Model] = select(None)

    /** Select a single value - returning an Optional value */
    def selectOption(id: Option[UUID])(implicit xa: Transactor[IO]): Future[Option[Model]] = {
      val updatedFilters = filters :+ id.map({ identity => fr"id = $id"})
      val query = (selectF ++ Fragments.whereAndOpt(updatedFilters: _*)).query[Model].option
      query.transact(xa).unsafeToFuture
    }

    /** Select a single value - returning an Optional value */
    def selectOption(id: UUID)(implicit xa: Transactor[IO]): Future[Option[Model]] = selectOption(Some(id))

    /** Select a single value - returning an Optional value */
    def selectOption(implicit xa: Transactor[IO]): Future[Option[Model]] = selectOption(None)
  }
}

