package com.azavea.rf.database.sort

import com.lonelyplanet.akka.http.extensions.Order
import com.azavea.rf.database.ExtendedPostgresDriver.api._


/**
  * Type class that composes multiple sort strategies for a specific table.
  *
  * @param rules  Array of field specific sort rules available for E
  * @tparam E     Lifted query type we expect to sort
  */
class QuerySorter[E](rules: QuerySort[E]*) {
  /**
    * Sort query by a single field in specified order
    * @param query  Slick query object
    * @param field  String representation of field to be ordered in query
    * @param ord    Desired order of the field
    * @tparam U     Class mapping for Query, unbound
    * @tparam C     Collection mapping for Query, unbound
    * @return       query instance with sort applied or unchanged if no rules match the field
    */
  def sort[U, C[_]](query: Query[E, U, C], field: String, ord: Order): Query[E, U, C] = {
    rules.foldLeft(query){ (q, r) => r(q, field, ord) }
  }

  /**
    * Sort query by fields specified in sortMap in .foreach traversal order
    * @param query    Slick query object
    * @param sortMap  Map of field, order tuples that express requested order for query record
    * @tparam U       Class mapping for Query, unbound
    * @tparam C       Collection mapping for Query, unbound
    * @return         query instance with sort applied or unchanged if no rules match the field
    */
  def sort[U, C[_]](query: Query[E, U, C], sortMap: Map[String, Order]): Query[E, U, C] = {
    sortMap.foldLeft(query) { case (q, (field, ord)) =>
      sort(query, field, ord)
    }
  }
}