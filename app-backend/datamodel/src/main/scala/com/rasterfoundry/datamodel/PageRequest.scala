package com.rasterfoundry.datamodel

final case class PageRequest(
    offset: Long,
    limit: Long,
    sort: Map[String, Order]
)
