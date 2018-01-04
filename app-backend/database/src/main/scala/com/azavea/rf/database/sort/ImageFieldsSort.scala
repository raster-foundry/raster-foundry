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
        query.sortBy(q => (f(q).rawDataBytes.byOrder(ord), f(q).id))
      case "filename" =>
        query.sortBy(q => (f(q).filename.byOrder(ord), f(q).id))
      case "sourceuri" =>
        query.sortBy(q => (f(q).filename.byOrder(ord), f(q).id))
      case _ => query
    }
  }
}
