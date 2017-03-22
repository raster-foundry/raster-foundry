package com.azavea.rf.database

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import slick.lifted.Rep
import slick.dbio.{DBIO, StreamingDBIO, Streaming}

import com.azavea.rf.datamodel.PaginatedResponse
import com.azavea.rf.database.{Database => DB}
import com.azavea.rf.database.ExtendedPostgresDriver.api.TableQuery
import com.azavea.rf.database.query.ListQueryResult

trait ActionRunner {

  def list[T](a: ListQueryResult[T], offset: Int, limit: Int)
          (implicit database: DB): Future[PaginatedResponse[T]] = {
    database.db.run(for {
      records <- a.records
      nRecords <- a.nRecords
    } yield {
      PaginatedResponse[T](
        nRecords,
        offset > 0,
        (offset + 1) * limit < nRecords,
        offset,
        limit,
        records
      )
    })

  }

  def readOne[T](a: DBIO[Option[T]])(implicit database: DB): Future[Option[T]] =
    database.db.run(a)

  def readOneDirect[T](a: DBIO[T])(implicit database: DB): Future[T] =
    database.db.run(a)

  def write[T](a: DBIO[T])(implicit database: DB): Future[T] = database.db.run(a)

  def update(a: DBIO[Int])(implicit database: DB): Future[Int] = database.db.run(a)

  def drop(a: DBIO[Int])(implicit database: DB): Future[Int] = database.db.run(a)
}
