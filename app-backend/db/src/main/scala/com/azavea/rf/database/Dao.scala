package com.azavea.rf.database

import com.azavea.rf.database.filter.Filterables
import com.azavea.rf.database.util._
import com.azavea.rf.datamodel._
import com.azavea.rf.database.Implicits._


import doobie._, doobie.implicits._
import doobie.postgres._, doobie.postgres.implicits._
import cats._, cats.data._, cats.effect.IO, cats.implicits._
import com.lonelyplanet.akka.http.extensions.PageRequest

import scala.concurrent.Future
import java.util.UUID

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
  def query: Dao.QueryBuilder[Model] = Dao.QueryBuilder[Model](selectF, tableF, List.empty)


  def ownerEditFilter(user: User): Option[Fragment] = {
    user.isInRootOrganization match {
      case true => None
      case _ => Some(fr"(organization_id = ${user.organizationId} OR owner = ${user.id})")
    }
  }

}

object Dao {

  case class QueryBuilder[Model: Composite](selectF: Fragment, tableF: Fragment, filters: List[Option[Fragment]]) {

    val countF = fr"SELECT count(*) FROM" ++ tableF
    val deleteF = fr"DELETE FROM" ++ tableF
    val existF = fr"SELECT 1 FROM" ++ tableF

    /** Add another filter to the query being constructed */
    def filter[M >: Model, T](thing: T)(implicit filterable: Filterable[M, T]): QueryBuilder[Model] =
      this.copy(filters = filters ++ filterable.toFilters(thing))

    def filter[M >: Model](id: UUID)(implicit filterable: Filterable[M, Option[Fragment]]): QueryBuilder[Model] = {
      this.copy(filters = filters ++ filterable.toFilters(Some(fr"id = ${id}")))
    }

    // Filter to validate access on an object type
    def authorize[M >: Model](user: User, objectType: ObjectType, actionType: ActionType)(implicit filterable: Filterable[M, Option[Fragment]]): QueryBuilder[Model] = {
      this.copy(filters = filters ++ filterable.toFilters(Some(
        fr"""id IN (
          -- Collect objects owned by the user
          SELECT A.id
          FROM""" ++ tableF ++ fr"""AS A
          WHERE A.owner = ${user.id}

          UNION ALL

          -- Collect objects the user has access to for non-group permissions
          SELECT A.id
          FROM""" ++ tableF ++ fr"""AS A
          JOIN access_control_rules acr ON
            acr.object_id::text = A.id::text AND
            acr.object_type = ${objectType} AND
            acr.action_type = ${actionType} AND
            -- Match if the ACR is an ALL
            acr.subject_type = 'ALL' OR
            -- Match if the ACR is per user
            (acr.subject_type = 'USER' AND acr.subject_id = ${user.id})

          UNION ALL

          -- Collect objects the user has access to for group permissions
          SELECT A.id
          FROM""" ++ tableF ++ fr"""AS A
          JOIN access_control_rules acr ON
            acr.object_id::text = A.id::text AND
            acr.object_type = ${objectType} AND
            acr.action_type = ${actionType}
          JOIN user_group_roles ugr ON
            ugr.user_id = ${user.id} AND
            acr.subject_type::text = ugr.group_type::text AND
            acr.subject_id::text = ugr.group_id::text
        )"""
      )))
    }

    // Filter to validate access to a specific object
    def authorize[M >: Model](user: User, objectType: ObjectType, objectId: UUID, actionType: ActionType)(implicit filterable: Filterable[M, Option[Fragment]]): QueryBuilder[Model] = {
      this.copy(filters = filters ++ filterable.toFilters(Some(
        fr"""(
          -- Match if the user owns the object
          owner = ${user.id} OR
          -- Match if the user is granted access via ALL or explicitly granted access
          (
            SELECT count(acr.id) > 0
            FROM access_control_rules AS acr
            WHERE
              (
                acr.subject_type = ${SubjectType.All.toString}::subject_type OR
                (
                  acr.subject_type = ${SubjectType.User.toString}::subject_type AND
                  acr.subject_id = ${user.id}
                )
              ) AND
              acr.object_id = ${objectId} AND
              acr.object_type = ${objectType} AND
              acr.action_type = ${actionType}
            LIMIT 1
          ) OR
          -- Match if the user is granted permission via group membership
          (
            SELECT count(acr.id) > 0
            FROM access_control_rules AS acr
            JOIN user_group_roles ugr ON
              acr.subject_type::text = ugr.group_type::text AND
              acr.subject_id::text = ugr.group_id::text
            WHERE
              acr.object_id = ${objectId} AND
              ugr.user_id = ${user.id} AND
              acr.action_type = ${actionType}
            LIMIT 1
          )
        )"""
      )))
    }

    def ownerFilterF(user: User): Option[Fragment] = {
      if (user.isInRootOrganization) {
        None
      } else {
        Some(fr"(organization_id = ${user.organizationId} OR owner = ${user.id})")
      }
    }

    def ownerFilter[M >: Model](user: User)(implicit filterable: Filterable[M, Option[Fragment]]): QueryBuilder[Model] = {
      this.copy(filters = filters ++ filterable.toFilters(ownerFilterF(user)))
    }

    def ownerFilterF2(user: User): Option[Fragment] = {
      if (user.isInRootOrganization) {
        None
      } else {
        Some(fr"(organization = ${user.organizationId} OR owner = ${user.id})")
      }
    }

    def ownerFilter2[M >: Model](user: User)(implicit filterable: Filterable[M, Option[Fragment]]): QueryBuilder[Model] = {
      this.copy(filters = filters ++ filterable.toFilters(ownerFilterF2(user)))
    }

    /** Provide a list of responses within the PaginatedResponse wrapper */
    def page(pageRequest: PageRequest, selectF: Fragment, countF: Fragment): ConnectionIO[PaginatedResponse[Model]] = {
      for {
        page <- (selectF ++ Fragments.whereAndOpt(filters: _*) ++ Page(pageRequest)).query[Model].list
        count <- (countF ++ Fragments.whereAndOpt(filters: _*)).query[Int].unique
      } yield {
        val hasPrevious = pageRequest.offset > 0
        val hasNext = (pageRequest.offset * pageRequest.limit) + 1 < count

        PaginatedResponse[Model](count, hasPrevious, hasNext, pageRequest.offset, pageRequest.limit, page)
      }
    }

    /** Provide a list of responses within the PaginatedResponse wrapper */
    def page(pageRequest: PageRequest): ConnectionIO[PaginatedResponse[Model]] =
      page(pageRequest, selectF, countF)

    def listQ(pageRequest: PageRequest): Query0[Model] =
      (selectF ++ Fragments.whereAndOpt(filters: _*) ++ Page(Some(pageRequest))).query[Model]

    /** Provide a list of responses */
    def list(pageRequest: PageRequest): ConnectionIO[List[Model]] = {
      listQ(pageRequest).list
    }

    def listQ(limit: Int): Query0[Model] =
      (selectF ++ Fragments.whereAndOpt(filters: _*) ++ fr"LIMIT $limit").query[Model]

    /** Provide a list of responses */
    def list(limit: Int): ConnectionIO[List[Model]] = {
      listQ(limit).list
    }

    def listQ(offset: Int, limit: Int): Query0[Model] =
      (selectF ++ Fragments.whereAndOpt(filters: _*) ++ fr"OFFSET $offset" ++ fr"LIMIT $limit").query[Model]

    /** Provide a list of responses */
    def list: ConnectionIO[List[Model]] = {
      (selectF ++ Fragments.whereAndOpt(filters: _*))
        .query[Model]
        .list
    }

    /** Provide a list of responses */
    def list(offset: Int, limit: Int): ConnectionIO[List[Model]] = {
      listQ(offset, limit).list
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
        .getOrElse(throw new Exception("Unsafe delete - delete requires filters"))
        .run
    }

    def exists: ConnectionIO[Boolean] = {
      (existF ++ Fragments.whereAndOpt(filters: _*) ++ fr"LIMIT 1")
        .query[Int]
        .list
        .map(!_.isEmpty)
    }

    def authorized(user: User, objectType: ObjectType, objectId: UUID, actionType: ActionType): ConnectionIO[Boolean] = {
      this
        .filter(objectId)
        .authorize(user, objectType, objectId, actionType)
        .exists
    }
  }
}
