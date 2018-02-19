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
abstract class Dao[Model: Composite] {

  val tableName: String

  /** The fragment which holds the associated table's name */
  def tableF = Fragment.const(tableName)

  /** An abstract select statement to be used for constructing queries */
  def selectF: Fragment

  /** Begin construction of a complex, filtered query */
  def query: Dao.QueryBuilder[Model] = Dao.QueryBuilder[Model](selectF, tableF, List.empty)
}

object Dao {

  case class QueryBuilder[Model: Composite](selectF: Fragment, tableF: Fragment, filters: List[Option[Fragment]]) {

    val countF = fr"SELECT count(*) FROM" ++ tableF
    val deleteF = fr"DELETE FROM" ++ tableF

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

    def listQ(pageRequest: PageRequest)(implicit xa: Transactor[IO]): Query0[Model] =
      (selectF ++ Fragments.whereAndOpt(filters: _*) ++ Page(Some(pageRequest))).query[Model]

    /** Provide a list of responses */
    def list(pageRequest: PageRequest)(implicit xa: Transactor[IO]): Future[List[Model]] = {
      val query = listQ(pageRequest).list
      query.transact(xa).unsafeToFuture
    }

    def listQ(limit: Int)(implicit xa: Transactor[IO]): Query0[Model] =
      (selectF ++ Fragments.whereAndOpt(filters: _*) ++ fr"LIMIT $limit").query[Model]

    /** Provide a list of responses */
    def list(limit: Int)(implicit xa: Transactor[IO]): Future[List[Model]] = {
      val query = listQ(limit).list
      query.transact(xa).unsafeToFuture
    }

    def listQ(offset: Int, limit: Int)(implicit xa: Transactor[IO]): Query0[Model] =
      (selectF ++ Fragments.whereAndOpt(filters: _*) ++ fr"OFFSET $offset" ++ fr"LIMIT $limit").query[Model]

    /** Provide a list of responses */
    def list(implicit xa: Transactor[IO]): Future[List[Model]] = {
      val query = (selectF ++ Fragments.whereAndOpt(filters: _*))
        .query[Model]
        .list

      query.transact(xa).unsafeToFuture
    }

    /** Provide a list of responses */
    def list(offset: Int, limit: Int)(implicit xa: Transactor[IO]): Future[List[Model]] = {
      val query = listQ(offset, limit).list
      query.transact(xa).unsafeToFuture
    }

    def selectQ: Query0[Model] =
      (selectF ++ Fragments.whereAndOpt(filters: _*)).query[Model]

    /** Select a single value - throw on failure */
    def select(implicit xa: Transactor[IO]): Future[Model] =
      selectQ.unique.transact(xa).unsafeToFuture

    /** Select a single value - returning an Optional value */
    def selectOption(implicit xa: Transactor[IO]): Future[Option[Model]] =
      selectQ.option.transact(xa).unsafeToFuture

    def selectOption(id: UUID)(implicit xa: Transactor[IO]): Future[Option[Model]] = {
      val selectStatement = selectF ++ fr"id = $id"
      selectStatement.query[Model].option.transact(xa).unsafeToFuture
    }

    def deleteQOption: Option[Update0] = {
      if (filters.length > 0) {
        Some((deleteF ++ Fragments.whereAndOpt(filters: _*)).update)
      }
      None
    }

    def delete(implicit xa: Transactor[IO]): Future[Int] =
      deleteQOption
        .getOrElse(throw new Exception("Unsafe delete - delete requires filters"))
        .run.transact(xa).unsafeToFuture
  }
}

