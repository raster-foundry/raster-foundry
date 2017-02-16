package com.azavea.rf.database.sort

import com.azavea.rf.database.fields.TimestampFields
import com.lonelyplanet.akka.http.extensions.Order
import com.azavea.rf.database.ExtendedPostgresDriver.api._

class TimestampSort[E, D <: TimestampFields](f: E => D) extends QuerySort[E] {
  def apply[U, C[_]](
    query: Query[E, U, C],
    field: String,
    ord: Order
  ): Query[E, U, C] = {
    field match {
      case "createdAt" => query.sortBy(f(_).createdAt.byOrder(ord))
      case "modifiedAt" => query.sortBy(f(_).modifiedAt.byOrder(ord))
      case _ => query
    }
  }
}
