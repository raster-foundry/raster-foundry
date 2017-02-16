package com.azavea.rf.database.sort


import com.lonelyplanet.akka.http.extensions.Order
import com.azavea.rf.database.ExtendedPostgresDriver.api._

/**
  * Class to be used for implicit method extension of slick Query object.
  * Implicit instance  of this class can be found in [[com.azavea.rf.database.sort]] package object.
  */
class QuerySortMethods[E, U, C[_]](query: Query[E, U, C]) {
  /**
    * Sort query by fields specified in sortMap in .foreach traversal order
    * @param sortMap  Map of field, order tuples that express requested order for query record
    */
  def sort(sortMap: Map[String, Order])(implicit sorter: QuerySorter[E]): Query[E, U, C] = {
    sorter.sort(query, sortMap)
  }
}
