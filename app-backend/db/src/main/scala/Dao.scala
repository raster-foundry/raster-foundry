package com.rasterfoundry.database

import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.database.filter.Filterables
import com.rasterfoundry.database.util._
import com.rasterfoundry.datamodel._
import com.rasterfoundry.datamodel.{Order, PageRequest}

import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.log._
import doobie.util.{Read, Write}
import doobie.{LogHandler => _, _}

import scala.concurrent.duration.FiniteDuration

import java.util.UUID
import cats.data.NonEmptyList

/**
  * This is abstraction over the listing of arbitrary types from the DB with filters/pagination
  */
abstract class Dao[Model: Read: Write] extends Filterables {

  val tableName: String

  val fieldNames: List[String] = List()

  /** Helper to use in selectF and avoid writing out */
  def selectFieldsF =
    Fragment.const(fieldNames.map(f => tableName ++ "." ++ f).mkString(", "))
  def insertFieldsF =
    Fragment.const(fieldNames.mkString(", "))

  /** The fragment which holds the associated table's name */
  def tableF = Fragment.const(tableName)

  /** An abstract select statement to be used for constructing queries */
  def selectF: Fragment

  /** Begin construction of a complex, filtered query */
  def query: Dao.QueryBuilder[Model] =
    Dao.QueryBuilder[Model](selectF, tableF, List.empty)

  def groupQuery(groups: NonEmptyList[Fragment]): Dao.GroupQueryBuilder[Model] =
    Dao.GroupQueryBuilder[Model](
      selectF,
      tableF,
      groups,
      Nil
    )
}

object Dao extends LazyLogging {

  implicit val logHandler: LogHandler = LogHandler {
    case Success(
          s: String,
          a: List[Any],
          e1: FiniteDuration,
          e2: FiniteDuration
        ) =>
      val queryString =
        s.lines.dropWhile(_.trim.isEmpty).toArray.mkString("\n  ")
      val logString = queryString
        .split("\\?", -1)
        .zip(a.map(s => "'" + s + "'"))
        .flatMap({ case (t1, t2) => List(t1, t2) })
        .mkString("")
      logger.debug(s"""Successful Statement Execution:
        |
        |  ${logString}
        |
        | arguments = [${a.mkString(", ")}]
        |   elapsed = ${e1.toMillis} ms exec + ${e2.toMillis} ms processing (${(e1 + e2).toMillis} ms total)
      """.stripMargin)

    case ProcessingFailure(s, a, e1, e2, t) =>
      val queryString =
        s.lines.dropWhile(_.trim.isEmpty).toArray.mkString("\n  ")
      val logString = queryString
        .split("\\?", -1)
        .zip(a.map(s => "'" + s + "'"))
        .flatMap({ case (t1, t2) => List(t1, t2) })
        .mkString("")
      logger.error(s"""Failed Resultset Processing:
        |
        |  ${logString}
        |
        | arguments = [${a.mkString(", ")}]
        |   elapsed = ${e1.toMillis} ms exec + ${e2.toMillis} ms processing (failed) (${(e1 + e2).toMillis} ms total)
        |   failure = ${t.getMessage}
      """.stripMargin)

    case ExecFailure(s, a, e1, t) =>
      val queryString =
        s.lines.dropWhile(_.trim.isEmpty).toArray.mkString("\n  ")
      val logString = queryString
        .split("\\?", -1)
        .zip(a.map(s => "'" + s + "'"))
        .flatMap({ case (t1, t2) => List(t1, t2) })
        .toList
        .mkString("")
      logger.error(s"""Failed Statement Execution:
        |
        |  ${logString}
        |
        | arguments = [${a.mkString(", ")}]
        |   elapsed = ${e1.toMillis} ms exec (failed)
        |   failure = ${t.getMessage}
      """.stripMargin)
  }

  // This case class represents
  final case class GroupQueryBuilder[Model: Read: Write](
      selectF: Fragment,
      tableF: Fragment,
      groups: NonEmptyList[Fragment],
      filters: List[Option[Fragment]]
  ) {

    /** Add another filter to the query being constructed */
    def filter[M >: Model, T](
        thing: T
    )(implicit filterable: Filterable[M, T]): GroupQueryBuilder[Model] =
      this.copy(filters = filters ++ filterable.toFilters(thing))

    def filter[M >: Model](
        thing: Fragment
    )(implicit filterable: Filterable[M, Fragment]): GroupQueryBuilder[Model] =
      thing match {
        case Fragment.empty => this
        case _              => this.copy(filters = filters ++ filterable.toFilters(thing))
      }

    def filter[M >: Model](id: UUID)(implicit
        filterable: Filterable[M, Option[Fragment]]
    ): GroupQueryBuilder[Model] = {
      this.copy(filters = filters ++ filterable.toFilters(Some(fr"id = ${id}")))
    }

    def filter[M >: Model](
        fragments: List[Option[Fragment]]
    ): GroupQueryBuilder[Model] = {
      this.copy(filters = filters ::: fragments)
    }

    private def noLimitListQ: Query0[Model] =
      (selectF ++ fr"FROM" ++ tableF ++ Fragments.whereAndOpt(
        filters: _*
      ) ++ fr"GROUP BY" ++ groups.intercalate(fr","))
        .query[Model]

    def list: ConnectionIO[List[Model]] = noLimitListQ.to[List]
  }

  final case class QueryBuilder[Model: Read: Write](
      selectF: Fragment,
      tableF: Fragment,
      filters: List[Option[Fragment]],
      countFragment: Option[Fragment] = None
  ) {

    val countF: Fragment =
      countFragment.getOrElse(fr"SELECT count(id) FROM" ++ tableF)
    val deleteF: Fragment = fr"DELETE FROM" ++ tableF
    val existF: Fragment = fr"SELECT 1 FROM" ++ tableF

    /** Add another filter to the query being constructed */
    def filter[M >: Model, T](
        thing: T
    )(implicit filterable: Filterable[M, T]): QueryBuilder[Model] =
      this.copy(filters = filters ++ filterable.toFilters(thing))

    def filter[M >: Model](
        thing: Fragment
    )(implicit filterable: Filterable[M, Fragment]): QueryBuilder[Model] =
      thing match {
        case Fragment.empty => this
        case _              => this.copy(filters = filters ++ filterable.toFilters(thing))
      }

    def filter[M >: Model](id: UUID)(implicit
        filterable: Filterable[M, Option[Fragment]]
    ): QueryBuilder[Model] = {
      this.copy(filters = filters ++ filterable.toFilters(Some(fr"id = ${id}")))
    }

    def filter[M >: Model](
        fragments: List[Option[Fragment]]
    ): QueryBuilder[Model] = {
      this.copy(filters = filters ::: fragments)
    }

    // This method exists temporarily to stand in for second-tier object authorization
    def ownedBy[M >: Model](user: User, objectId: UUID): QueryBuilder[Model] =
      this.filter(objectId).filter(user)

    def ownedByOrSuperUser[M >: Model](
        user: User,
        objectId: UUID
    ): QueryBuilder[Model] = {
      if (user.isSuperuser) {
        this.filter(objectId)
      } else {
        this.filter(objectId).filter(user)
      }
    }

    def pageOffset[T: Read](pageRequest: PageRequest): ConnectionIO[List[T]] =
      (selectF ++ Fragments.whereAndOpt(filters: _*) ++ Page(pageRequest))
        .query[T]
        .to[List]

    def hasNext(pageRequest: PageRequest): ConnectionIO[Boolean] = {
      (existF ++ Fragments.whereAndOpt(filters: _*) ++ Page(
        pageRequest.copy(offset = pageRequest.offset + 1)
      )).query[Boolean]
        .to[List]
        .map(_.nonEmpty)
    }

    /** Provide a list of responses within the PaginatedResponse wrapper */
    def page[T: Read](
        pageRequest: PageRequest,
        selectF: Fragment,
        countF: Fragment,
        orderClause: Map[String, Order],
        doCount: Boolean,
        groupClause: Fragment = Fragment.empty
    ): ConnectionIO[PaginatedResponse[T]] = {
      for {
        page <-
          (selectF ++ Fragments.whereAndOpt(filters: _*) ++ groupClause ++ Page(
            pageRequest.copy(sort = orderClause ++ pageRequest.sort)
          )).query[T]
            .to[List]
        (count: Int, hasNext: Boolean) <- doCount match {
          case true => {
            (countF ++ Fragments.whereAndOpt(filters: _*))
              .query[Int]
              .unique map { count =>
              (count, (pageRequest.offset + 1) * pageRequest.limit < count)
            }
          }
          case false => {
            hasNext(pageRequest) map {
              (-1, _)
            }
          }
        }
      } yield {
        val hasPrevious = pageRequest.offset > 0

        PaginatedResponse[T](
          count,
          hasPrevious,
          hasNext,
          pageRequest.offset,
          pageRequest.limit,
          page
        )
      }
    }

    /** Provide a list of responses within the PaginatedResponse wrapper */
    def page(
        pageRequest: PageRequest,
        orderClause: Map[String, Order]
    ): ConnectionIO[PaginatedResponse[Model]] =
      page(pageRequest, selectF, countF, orderClause, true)

    def page(
        pageRequest: PageRequest,
        orderClause: Map[String, Order],
        doCount: Boolean
    ): ConnectionIO[PaginatedResponse[Model]] =
      page(pageRequest, selectF, countF, orderClause, doCount)

    def page(pageRequest: PageRequest): ConnectionIO[PaginatedResponse[Model]] =
      page(pageRequest, selectF, countF, Map.empty[String, Order], true)

    def page(
        pageRequest: PageRequest,
        groupClause: Fragment
    ): ConnectionIO[PaginatedResponse[Model]] =
      page(
        pageRequest,
        selectF,
        countF,
        Map.empty[String, Order],
        true,
        groupClause
      )

    def listQ(pageRequest: PageRequest): Query0[Model] =
      (selectF ++ Fragments.whereAndOpt(filters: _*) ++ Page(Some(pageRequest)))
        .query[Model]

    /** Provide a list of responses */
    def list(pageRequest: PageRequest): ConnectionIO[List[Model]] = {
      listQ(pageRequest).to[List]
    }

    /** Short circuit for quickly getting an approximate count for large queries (e.g. scenes) * */
    def sceneCountIO(exactCountOption: Option[Boolean]): ConnectionIO[Int] = {
      val countQuery = countF ++ Fragments.whereAndOpt(filters: _*)
      val over100IO: ConnectionIO[Boolean] =
        (fr"SELECT EXISTS(" ++ (selectF ++ Fragments.whereAndOpt(
          filters: _*
        ) ++ fr"offset 100") ++ fr")")
          .query[Boolean]
          .unique
      over100IO.flatMap(over100 => {
        (exactCountOption, over100) match {
          case (Some(true), _) | (_, false) =>
            countQuery.query[Int].unique
          case _ =>
            100.pure[ConnectionIO]
        }
      })
    }

    def listQ(limit: Int): Query0[Model] =
      (selectF ++ Fragments.whereAndOpt(filters: _*) ++ fr"LIMIT $limit")
        .query[Model]

    /** Filter for objects of type model without a limit
      *
      * This is private so that consumers can't manipulate it directly.
      * If you need an unlimited number of results, see the no argument
      * list method or stream
      */
    private def noLimitListQ: Query0[Model] =
      (selectF ++ Fragments.whereAndOpt(filters: _*))
        .query[Model]

    /** Provide a stream of responses */
    def stream: fs2.Stream[ConnectionIO, Model] =
      noLimitListQ.stream

    /** Provide a list of responses */
    def list(limit: Int): ConnectionIO[List[Model]] = {
      listQ(limit).to[List]
    }

    def listQ(offset: Int, limit: Int): Query0[Model] =
      (selectF ++ Fragments.whereAndOpt(
        filters: _*
      ) ++ fr"OFFSET $offset" ++ fr"LIMIT $limit")
        .query[Model]

    def listQ(offset: Int, limit: Int, orderClause: Fragment): Query0[Model] =
      (selectF ++ Fragments.whereAndOpt(
        filters: _*
      ) ++ orderClause ++ fr"OFFSET $offset" ++ fr"LIMIT $limit")
        .query[Model]

    /** Provide a list of responses */
    def list: ConnectionIO[List[Model]] = noLimitListQ.to[List]

    /** Provide a list of responses */
    def list(offset: Int, limit: Int): ConnectionIO[List[Model]] = {
      listQ(offset, limit).to[List]
    }

    def list(
        offset: Int,
        limit: Int,
        orderClause: Fragment
    ): ConnectionIO[List[Model]] = {
      listQ(offset, limit, orderClause).to[List]
    }

    def selectQ: Query0[Model] =
      (selectF ++ Fragments.whereAndOpt(filters: _*)).query[Model]

    /** Select a single value - returning an Optional value */
    def selectOption: ConnectionIO[Option[Model]] =
      selectQ.option

    /** Select a single value - throw on failure */
    def select: ConnectionIO[Model] = {
      selectQ.unique
    }

    def deleteQOption: Option[Update0] = {
      if (filters.isEmpty) {
        None
      } else {
        Some((deleteF ++ Fragments.whereAndOpt(filters: _*)).update)
      }
    }

    def delete: ConnectionIO[Int] = {
      deleteQOption
        .getOrElse(
          throw new Exception("Unsafe delete - delete requires filters")
        )
        .run
    }

    def count: ConnectionIO[Long] = {
      (countF ++ Fragments.whereAndOpt(filters: _*)).query[Long].unique
    }

    def exists: ConnectionIO[Boolean] = {
      (existF ++ Fragments.whereAndOpt(filters: _*) ++ fr"LIMIT 1")
        .query[Int]
        .to[List]
        .map(_.nonEmpty)
    }
  }
}
