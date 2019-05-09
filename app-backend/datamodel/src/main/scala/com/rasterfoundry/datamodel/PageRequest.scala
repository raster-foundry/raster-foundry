package com.rasterfoundry.datamodel

final case class PageRequest(offset: Int, limit: Int, sort: Map[String, Order])
