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
        query.sortBy(q => (f(q).datasource.byOrder(ord), f(q).id))
      case "month" =>
        query.sortBy(q => (datePart("month", f(q).acquisitionDate).byOrder(ord), f(q).id))
      case "acquisitionDatetime" =>
        query.sortBy(q =>
          (f(q).acquisitionDate.getOrElse(f(q).createdAt).byOrder(ord), f(q).createdAt.byOrder(ord), f(q).id)
        )
      case "sunAzimuth" =>
        query.sortBy(q => (f(q).sunAzimuth.byOrder(ord), f(q).id))
      case "sunElevation" =>
        query.sortBy(q => (f(q).sunElevation.byOrder(ord), f(q).id))
      case "cloudCover" =>
        query.sortBy(q => (f(q).cloudCover.byOrder(ord), f(q).id))
      case _ => query
    }
  }
}
