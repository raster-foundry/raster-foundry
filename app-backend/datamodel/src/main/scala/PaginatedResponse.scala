package com.rasterfoundry.datamodel

import io.circe.generic.JsonCodec

/**
  * Case class for paginated results
  *
  * @param count number of total results available
  * @param hasPrevious whether or not previous results are available
  * @param hasNext whether or not additional results are available
  * @param page current page of results
  * @param pageSize number of results per page
  * @param results sequence of results for a page
  */
@JsonCodec
final case class PaginatedResponse[A](
    count: Long,
    hasPrevious: Boolean,
    hasNext: Boolean,
    page: Long,
    pageSize: Long,
    results: Seq[A]
)
