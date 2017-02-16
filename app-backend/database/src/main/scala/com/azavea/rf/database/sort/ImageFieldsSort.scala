package com.azavea.rf.database.sort

import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.database.fields.ImageFields
import com.lonelyplanet.akka.http.extensions.Order

class ImageFieldsSort[E, D <: ImageFields](f: E => D) extends QuerySort[E] {
  def apply[U, C[_]](
    query: Query[E, U, C],
    field: String,
    ord: Order
  ): Query[E, U, C] = {
    field match {
      case "name" =>
        query.sortBy(f(_).rawDataBytes.byOrder(ord))
      case "filename" =>
        query.sortBy(f(_).filename.byOrder(ord))
      case "sourceuri" =>
        query.sortBy(f(_).filename.byOrder(ord))
      case _ => query
    }
  }
}
