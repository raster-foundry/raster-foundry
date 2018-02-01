package com.azavea.rf.database

import com.azavea.rf.database.meta.RFMeta._
import com.azavea.rf.database.util._
import com.azavea.rf.datamodel._

import doobie._, doobie.implicits._
import doobie.postgres._, doobie.postgres.implicits._
import cats._, cats.data._, cats.effect.IO, cats.implicits._
import com.lonelyplanet.akka.http.extensions.PageRequest

import scala.concurrent.Future


/**
 * This is abstraction over the listing of arbitrary types from the DB with filters/pagination
 */
abstract class Dao[Model: Composite](tableName: String) {

  /** The fragment which holds the associated table's name */
  val tableF = Fragment.const(tableName)

  /** An abstract select statement to be used for constructing queries */
  def selectF: Fragment

  /** A fragment which holds count SQL */
  val countF = (sql"SELECT count(*) FROM" ++ tableF)

  /** List query. Useful for printing out sql via the `sql` method */
  def listQ(
    filters: List[Option[Fragment]],
    pageRequest: Option[PageRequest]
  ): Query0[Model] = {

    (selectF ++ Fragments.whereAndOpt(filters: _*) ++ Page(pageRequest))
      .query[Model]
  }

  /** ConnectionIO (therefore composable) for listing (with filters/pages) */
  def list(
    filters: List[Option[Fragment]],
    pageRequest: Option[PageRequest]
  ): ConnectionIO[List[Model]] = listQ(filters, pageRequest).list

  /** Returns a page of results wrapped in a future */
  def page(
    filters: List[Option[Fragment]],
    pageRequest: PageRequest
  )(implicit xa: Transactor[IO]): Future[PaginatedResponse[Model]] = {
    val transaction = for {
      page <- list(filters, Some(pageRequest))
      count <- (countF ++ Fragments.whereAndOpt(filters: _*)).query[Int].unique
    } yield {
      val hasPrevious = pageRequest.offset > 0
      val hasNext = (pageRequest.offset * pageRequest.limit) + 1 < count

      PaginatedResponse[Model](count, hasPrevious, hasNext, pageRequest.offset, pageRequest.limit, page)
    }
    transaction.transact(xa).unsafeToFuture
  }
}

