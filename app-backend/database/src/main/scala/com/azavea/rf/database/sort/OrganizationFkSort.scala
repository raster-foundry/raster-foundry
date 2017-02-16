package com.azavea.rf.database.sort

import com.azavea.rf.database.fields.OrganizationFkFields
import com.lonelyplanet.akka.http.extensions.Order
import com.azavea.rf.database.ExtendedPostgresDriver.api._

class OrganizationFkSort[E, D <: OrganizationFkFields](f: E => D) extends QuerySort[E] {
  def apply[U, C[_]](
    query: Query[E, U, C],
    field: String,
    ord: Order
  ): Query[E, U, C] = {
    field match {
      case "organization" => query.sortBy(f(_).organizationId.byOrder(ord))
      case _ => query
    }
  }
}
