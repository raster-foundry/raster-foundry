package com.azavea.rf.database.sort

import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.database.fields.VisibilityField
import com.lonelyplanet.akka.http.extensions.Order

class VisibilitySort[E, D <: VisibilityField](f: E => D) extends QuerySort[E] {
  def apply[U, C[_]](
    query: Query[E, U, C],
    field: String,
    ord: Order
  ): Query[E, U, C] = {
    field match {
      case "visibility" => query.sortBy(f(_).visibility.byOrder(ord))
      case _ => query
    }
  }
}
