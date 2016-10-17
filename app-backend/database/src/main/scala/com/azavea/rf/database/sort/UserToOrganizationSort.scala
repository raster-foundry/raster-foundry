package com.azavea.rf.database.sort

import com.azavea.rf.database.fields.UserToOrganizationFields
import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.lonelyplanet.akka.http.extensions.Order

class UserToOrganizationSort[E, D <: UserToOrganizationFields](f: E => D) extends QuerySort[E] {
  def apply[U, C[_]](
    query: Query[E, U, C],
    field: String,
    ord: Order
  ): Query[E, U, C] = {
    field match {
      case "id" =>
        query.sortBy(f(_).userId.byOrder(ord))
      case "role" =>
        query.sortBy(f(_).role.byOrder(ord))
      case _ => query
    }
  }
}

