package com.azavea.rf.database.sort

import com.azavea.rf.database.fields.{NameField, ProjectFields}
import com.lonelyplanet.akka.http.extensions.Order
import com.azavea.rf.database.ExtendedPostgresDriver.api._

class NameFieldSort[E, D <: NameField](f: E => D) extends QuerySort[E] {
  def apply[U, C[_]](
    query: Query[E, U, C],
    field: String,
    ord: Order
  ): Query[E, U, C] = {
    field match {
      case "name" =>
        query.sortBy(f(_).name.byOrder(ord))
      case _ => query
    }
  }
}
