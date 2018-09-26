package com.azavea.rf.database

import java.util.UUID

import cats.implicits._
import com.azavea.rf.database.Implicits._
import com.azavea.rf.database.filter.Filterables
import com.azavea.rf.database.util._
import com.azavea.rf.datamodel._
import com.lonelyplanet.akka.http.extensions.PageRequest
import doobie.{LogHandler => _, _}
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import doobie.util.log._
import com.typesafe.scalalogging.LazyLogging
import scala.concurrent.duration.FiniteDuration

/**
  * This is abstraction over the listing of arbitrary types from the DB with filters/pagination
  */
abstract class Dao[Model: Composite] extends Filterables {

  val tableName: String

  /** The fragment which holds the associated table's name */
  def tableF = Fragment.const(tableName)

  /** An abstract select statement to be used for constructing queries */
  def selectF: Fragment

  /** Begin construction of a complex, filtered query */
  def query: Dao.QueryBuilder[Model] =
    Dao.QueryBuilder[Model](selectF, tableF, List.empty)

  def authTableF(
      user: User,
      objectType: ObjectType,
      ownershipTypeO: Option[String],
      groupTypeO: Option[GroupType],
      groupIdO: Option[UUID]): (Option[Fragment], List[Option[Fragment]]) = {
    val ownedF: Fragment = fr"""
      SELECT id as object_id FROM""" ++ tableF ++ fr"""WHERE owner = ${user.id}
    """
    val sharedF: Fragment = fr"""
      SELECT acr.object_id
      FROM access_control_rules acr
      WHERE
        acr.is_active = true
        AND acr.object_type = ${objectType}
        AND acr.action_type = ${ActionType.View.toString}::action_type
        AND -- Match if the ACR is an ALL or per user
        (acr.subject_type = 'ALL' OR (acr.subject_type = 'USER' AND acr.subject_id = ${user.id}))
    """
    val inheritedBaseF: Fragment = fr"""
      SELECT acr.object_id
      FROM access_control_rules acr
      JOIN user_group_roles ugr ON acr.subject_type::text = ugr.group_type::text
      AND acr.subject_id::text = ugr.group_id::text
      WHERE
        acr.is_active = true
        AND ugr.user_id = ${user.id}
        AND acr.object_type = ${objectType}
        AND acr.action_type = ${ActionType.View.toString}::action_type
    """
    val inheritedF: Fragment = (groupTypeO, groupIdO) match {
      case (Some(groupType), Some(groupId)) => inheritedBaseF ++ fr"""
       AND ugr.group_type = ${groupType}
       AND ugr.group_id = ${groupId}
     """
      case _                                => inheritedBaseF
    }
    ownershipTypeO match {
      // owned by the requesting user only
      case Some(ownershipType) if ownershipType == "owned" =>
        (None, List(Some(fr"owner = ${user.id}")))
      // shared to the requesting user directly, across platform, or due to group membership
      case Some(ownershipType) if ownershipType == "shared" =>
        (Some(
           fr"INNER JOIN (" ++ sharedF ++ fr"UNION ALL" ++ inheritedF ++ fr") as object_ids ON" ++
             Fragment.const(s"${tableName}.id") ++ fr"= object_ids.object_id"),
         List(Some(fr"owner <> ${user.id}")))

      // shared to the requesting user due to group membership
      case Some(ownershipType) if ownershipType == "inherited" =>
        (Some(
           fr"INNER JOIN (" ++ inheritedF ++ fr") as object_ids ON" ++
             Fragment.const(s"${tableName}.id") ++ fr"= object_ids.object_id"),
         List.empty)
      // the default
      case _ =>
        (Some(
           fr"INNER JOIN (" ++ ownedF ++ fr"UNION ALL" ++ sharedF ++ fr"UNION ALL" ++
             inheritedF ++ fr") as object_ids ON" ++ Fragment.const(
             s"${tableName}.id") ++
             fr"= object_ids.object_id"),
         List.empty)
    }
  }
}

object Dao extends LazyLogging {

  implicit val logHandler: LogHandler = LogHandler {
    case Success(s: String,
                 a: List[Any],
                 e1: FiniteDuration,
                 e2: FiniteDuration) =>
      val queryString = s.lines.dropWhile(_.trim.isEmpty).mkString("\n  ")
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
      val queryString = s.lines.dropWhile(_.trim.isEmpty).mkString("\n  ")
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
      val queryString = s.lines.dropWhile(_.trim.isEmpty).mkString("\n  ")
      val logString = queryString
        .split("\\?", -1)
        .zip(a.map(s => "'" + s + "'"))
        .flatMap({ case (t1, t2) => List(t1, t2) })
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

  final case class QueryBuilder[Model: Composite](
      selectF: Fragment,
      tableF: Fragment,
      filters: List[Option[Fragment]]) {

    val countF: Fragment = fr"SELECT count(distinct(id)) FROM" ++ tableF
    val deleteF: Fragment = fr"DELETE FROM" ++ tableF
    val existF: Fragment = fr"SELECT 1 FROM" ++ tableF

    /** Add another filter to the query being constructed */
    def filter[M >: Model, T](thing: T)(
        implicit filterable: Filterable[M, T]): QueryBuilder[Model] =
      this.copy(filters = filters ++ filterable.toFilters(thing))

    def filter[M >: Model](thing: Fragment)(
        implicit filterable: Filterable[M, Fragment]): QueryBuilder[Model] =
      thing match {
        case Fragment.empty => this
        case _              => this.copy(filters = filters ++ filterable.toFilters(thing))
      }

    def filter[M >: Model](id: UUID)(
        implicit filterable: Filterable[M, Option[Fragment]])
      : QueryBuilder[Model] = {
      this.copy(filters = filters ++ filterable.toFilters(Some(fr"id = ${id}")))
    }

    def filter[M >: Model](
        fragments: List[Option[Fragment]]): QueryBuilder[Model] = {
      this.copy(filters = filters ::: fragments)
    }

    // This method exists temporarily to stand in for second-tier object authorization
    def ownedBy[M >: Model](user: User, objectId: UUID): QueryBuilder[Model] =
      this.filter(objectId).filter(user)

    def ownedByOrSuperUser[M >: Model](user: User,
                                       objectId: UUID): QueryBuilder[Model] = {
      if (user.isSuperuser) {
        this.filter(objectId)
      } else {
        this.filter(objectId).filter(user)
      }
    }

    def pageOffset[T: Composite](
        pageRequest: PageRequest): ConnectionIO[List[T]] =
      (selectF ++ Fragments.whereAndOpt(filters: _*) ++ Page(pageRequest))
        .query[T]
        .to[List]

    /** Provide a list of responses within the PaginatedResponse wrapper */
    def page[T: Composite](
        pageRequest: PageRequest,
        selectF: Fragment,
        countF: Fragment,
        orderClause: Fragment): ConnectionIO[PaginatedResponse[T]] = {
      for {
        page <- (selectF ++ Fragments.whereAndOpt(filters: _*) ++ orderClause ++ Page(
          pageRequest)).query[T].to[List]
        count <- (countF ++ Fragments.whereAndOpt(filters: _*))
          .query[Int]
          .unique
      } yield {
        val hasPrevious = pageRequest.offset > 0
        val hasNext = (pageRequest.offset * pageRequest.limit) + 1 < count

        PaginatedResponse[T](count,
                             hasPrevious,
                             hasNext,
                             pageRequest.offset,
                             pageRequest.limit,
                             page)
      }
    }

    /** Provide a list of responses within the PaginatedResponse wrapper */
    def page(pageRequest: PageRequest,
             orderClause: Fragment): ConnectionIO[PaginatedResponse[Model]] =
      page(pageRequest, selectF, countF, orderClause)

    def listQ(pageRequest: PageRequest): Query0[Model] =
      (selectF ++ Fragments.whereAndOpt(filters: _*) ++ Page(Some(pageRequest)))
        .query[Model]

    /** Provide a list of responses */
    def list(pageRequest: PageRequest): ConnectionIO[List[Model]] = {
      listQ(pageRequest).to[List]
    }

    /** Short circuit for quickly getting an approximate count for large queries (e.g. scenes) **/
    def sceneCountIO: ConnectionIO[Int] = {
      val countQuery = countF ++ Fragments.whereAndOpt(filters: _*)
      val over100IO: ConnectionIO[Boolean] =
        (fr"SELECT EXISTS(" ++ (selectF ++ Fragments.whereAndOpt(filters: _*) ++ fr"offset 100") ++ fr")")
          .query[Boolean]
          .unique
      over100IO flatMap {
        {
          case true  => 100.pure[ConnectionIO]
          case false => countQuery.query[Int].unique
        }
      }
    }

    def listQ(limit: Int): Query0[Model] =
      (selectF ++ Fragments.whereAndOpt(filters: _*) ++ fr"LIMIT $limit")
        .query[Model]

    /** Provide a list of responses */
    def list(limit: Int): ConnectionIO[List[Model]] = {
      listQ(limit).to[List]
    }

    def listQ(offset: Int, limit: Int): Query0[Model] =
      (selectF ++ Fragments.whereAndOpt(filters: _*) ++ fr"OFFSET $offset" ++ fr"LIMIT $limit")
        .query[Model]

    def listQ(offset: Int, limit: Int, orderClause: Fragment): Query0[Model] =
      (selectF ++ Fragments.whereAndOpt(filters: _*) ++ orderClause ++ fr"OFFSET $offset" ++ fr"LIMIT $limit")
        .query[Model]

    /** Provide a list of responses */
    def list: ConnectionIO[List[Model]] = {
      (selectF ++ Fragments.whereAndOpt(filters: _*))
        .query[Model]
        .to[List]
    }

    /** Provide a list of responses */
    def list(offset: Int, limit: Int): ConnectionIO[List[Model]] = {
      listQ(offset, limit).to[List]
    }

    def list(offset: Int,
             limit: Int,
             orderClause: Fragment): ConnectionIO[List[Model]] = {
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
          throw new Exception("Unsafe delete - delete requires filters"))
        .run
    }

    def exists: ConnectionIO[Boolean] = {
      (existF ++ Fragments.whereAndOpt(filters: _*) ++ fr"LIMIT 1")
        .query[Int]
        .to[List]
        .map(_.nonEmpty)
    }
  }
}
