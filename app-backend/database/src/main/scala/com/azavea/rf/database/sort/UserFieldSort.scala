package com.azavea.rf.database.sort

import com.azavea.rf.database.fields.UserFields
import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.lonelyplanet.akka.http.extensions.Order

class UserFieldSort[E, D <: UserFields](f: E => D) extends QuerySort[E] {
  def apply[U, C[_]](
    query: Query[E, U, C],
    field: String,
    ord: Order
  ): Query[E, U, C] = {
    field match {
      case "id" =>
        query.sortBy(f(_).id.byOrder(ord))
      case "role" =>
        query.sortBy(f(_).role.byOrder(ord))
      case _ => query
    }
  }
}

