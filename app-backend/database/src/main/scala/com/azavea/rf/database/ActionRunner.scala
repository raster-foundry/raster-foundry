package com.azavea.rf.database

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Success, Failure}

import slick.lifted.Rep
import slick.dbio.{DBIO, StreamingDBIO, Streaming}

import com.azavea.rf.datamodel._
import com.azavea.rf.datamodel.RelatedManager._
import com.azavea.rf.database.{Database => DB}
import com.azavea.rf.database.ExtendedPostgresDriver.api.TableQuery
import com.azavea.rf.database.query._

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

  def write[T](a: DBIO[T])(implicit database: DB): Future[T] = database.db.run(a)

  def update(a: DBIO[Int])(implicit database: DB): Future[Int] = {
    database.db.run(a) map {
      case 1 => 1
      case c => throw new IllegalStateException(
        s"Error updating: update result expected to be 1, was $c"
      )
    }
  }

  def drop(a: DBIO[Int])(implicit database: DB): Future[Int] = database.db.run(a)

  def withRelatedSingle1[T : HasRelations1, B, C](a: DBIO[(T, B)])
                        (implicit ev: HasRelations1.Aux[T, B, C], database: DB): Future[C] = {
    database.db.run(a) map {
      case (r1, r2) => ev.relates(r1, r2)
    }
  }

  def withRelatedOption1[T : HasRelations1, B, C](a: DBIO[(Option[T], B)])
                        (implicit ev: HasRelations1.Aux[T, B, C], database: DB): Future[Option[C]] = {
    database.db.run(a) map {
      case (Some(r1), r2) => Some(ev.relates(r1, r2))
      case _ => None
    }
  }

  def withRelatedSeq1[T : CanBuildFromRecords2, B, C](a: ListQueryResult[(B, C)],
                                                      offset: Int, limit: Int)
                     (implicit ev: CanBuildFromRecords2.Aux[T, B, C], database: DB):
      Future[PaginatedResponse[T]] = {
    database.db.run(
      for {
        records <- a.records
        nRecords <- a.nRecords
      } yield {
        // Coerce fromRecords to a Seq because PaginatedResponse requires it
        // It starts as an Iterable[T]
        PaginatedResponse[T](
          nRecords,
          offset > 0,
          (offset + 1) * limit < nRecords,
          offset,
          limit,
          ev.fromRecords(records).asInstanceOf[Seq[T]]
        )
      }
    )
  }

  def withRelatedSingle3[T : HasRelations2, B, C, D](a: DBIO[(T, B, C)])
                        (implicit ev: HasRelations2.Aux[T, B, C, D], database: DB): Future[D] = {
    database.db.run(a) map {
      case (r1, r2, r3) => ev.relates(r1, r2, r3)
    }
  }

  def withRelatedOption4[T : CanBuildFromRecords4, B, C, D, E](a: DBIO[Seq[(B, C, D, E)]])
                        (implicit ev: CanBuildFromRecords4.Aux[T, B, C, D, E], database: DB):
      Future[Option[T]] = {
    database.db.run(a) map {
      case results => ev.fromRecords(results).headOption
      case _ => None
    }
  }
}
