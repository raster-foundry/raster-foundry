package com.azavea.rf.database.sort

import com.azavea.rf.database.fields.ProjectFields
import com.lonelyplanet.akka.http.extensions.Order
import com.azavea.rf.database.ExtendedPostgresDriver.api._

class ProjectFieldsSort[E, D <: ProjectFields](f: E => D) extends QuerySort[E] {
  def apply[U, C[_]](
    query: Query[E, U, C],
    field: String,
    ord: Order
  ): Query[E, U, C] = {
    field match {
      case "name" =>
        query.sortBy(f(_).name.byOrder(ord))
      case "slugLabel" =>
        query.sortBy(f(_).slugLabel.byOrder(ord))
      case _ => query
    }
  }
}
