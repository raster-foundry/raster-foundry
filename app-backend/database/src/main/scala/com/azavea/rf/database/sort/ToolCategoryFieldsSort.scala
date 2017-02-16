package com.azavea.rf.database.sort

import com.azavea.rf.database.fields.ToolCategoryFields
import com.lonelyplanet.akka.http.extensions.Order
import com.azavea.rf.database.ExtendedPostgresDriver.api._

class ToolCategoryFieldsSort[E, D <: ToolCategoryFields](f: E => D) extends QuerySort[E] {
  def apply[U, C[_]](
    query: Query[E, U, C],
    field: String,
    ord: Order
  ): Query[E, U, C] = {
    field match {
      case "category" =>
        query.sortBy(f(_).category.byOrder(ord))
      case _ => query
    }
  }
}
