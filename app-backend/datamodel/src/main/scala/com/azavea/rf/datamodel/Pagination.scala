package com.azavea.rf.datamodel

import spray.json._
import spray.json.DefaultJsonProtocol._

/**
 * Case class for paginated results
 *
 * @param count number of total results available
 * @param hasPrevious whether or not previous results are available
 * @param hasNext whether or not additional results are available
 * @param page current page of results
 * @param pageSize number of results per page
 * @parma results sequence of results for a page
 */
case class PaginatedResponse[A](
  count: Int,
  hasPrevious: Boolean,
  hasNext: Boolean,
  page: Int,
  pageSize: Int,
  results: Seq[A]
)

case class PageRequest(offset: Int, limit: Int, sort: Map[String, Order])

sealed trait Order

object Order {

  case object Asc extends Order

  case object Desc extends Order

}
