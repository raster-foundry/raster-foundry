package com.azavea.rf.database.sort


import slick.ast.TypedType
import com.lonelyplanet.akka.http.extensions.Order
import com.azavea.rf.database.ExtendedPostgresDriver.api._

/**
  * Interface for a type class that may sort a query by a string representation for a field.
  * In case that a subclass of this interface does not know how to sort the query by the requested field,
  * the query should be returned unchanged. This requirement allows effects of multiple instances of [[QuerySort]]
  * to be composed through function composition on a query object.
  *
  * Subclasses of this interface are expected to request an addition type argument D and a function that maps
  * E to D. Constraints on D allows concrete fields to be invoked. Presence of a mapping E => D allows the
  * use of this interface on queries composed from table, as happens in case of joins.
  *
  * @tparam E Lifted representation of query fields
  */
trait QuerySort[E] {
  def apply[U, C[_]](
    query: Query[E, U, C],
    field: String,
    ord: Order
  ): Query[E, U, C]

  implicit class sortByOrder[T: TypedType](column: Rep[T]) {
    def byOrder(ord: Order) = ord match {
      case Order.Asc => column.asc
      case Order.Desc => column.desc
    }
  }
}
