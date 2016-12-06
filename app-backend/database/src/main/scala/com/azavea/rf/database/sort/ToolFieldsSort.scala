package com.azavea.rf.database.sort

import com.azavea.rf.database.fields.ToolFields
import com.lonelyplanet.akka.http.extensions.Order
import com.azavea.rf.database.ExtendedPostgresDriver.api._

class ToolFieldsSort[E, D <: ToolFields](f: E => D) extends QuerySort[E] {
  def apply[U, C[_]](
    query: Query[E, U, C],
    field: String,
    ord: Order
  ): Query[E, U, C] = {
    field match {
      case "title" =>
        query.sortBy(f(_).title.byOrder(ord))
      case _ => query
    }
  }
}
