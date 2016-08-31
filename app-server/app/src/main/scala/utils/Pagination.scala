package com.azavea.rf.utils


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
