package com.azavea.rf.database.sort

import com.azavea.rf.database.fields.SceneFields
import com.azavea.rf.database.tables.Scenes
import com.lonelyplanet.akka.http.extensions.Order
import com.azavea.rf.database.ExtendedPostgresDriver.api._

class SceneFieldsSort[E, D <: SceneFields](f: E => D) extends QuerySort[E] {
  import Scenes.datePart

  def apply[U, C[_]](
    query: Query[E, U, C],
    field: String,
    ord: Order
  ): Query[E, U, C] = {
    field match {
      case "datasource" =>
        query.sortBy(f(_).datasource.byOrder(ord))
      case "month" =>
        query.sortBy(q => datePart("month", f(q).acquisitionDate).byOrder(ord))
      case "acquisitionDatetime" =>
        query.sortBy(f(_).acquisitionDate.byOrder(ord))
      case "sunAzimuth" =>
        query.sortBy(f(_).sunAzimuth.byOrder(ord))
      case "sunElevation" =>
        query.sortBy(f(_).sunElevation.byOrder(ord))
      case "cloudCover" =>
        query.sortBy(f(_).cloudCover.byOrder(ord))
      case _ => query
    }
  }
}
