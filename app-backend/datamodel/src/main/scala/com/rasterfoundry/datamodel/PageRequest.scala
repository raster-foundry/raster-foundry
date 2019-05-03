package com.rasterfoundry.datamodel

case class PageRequest(offset: Int, limit: Int, sort: Map[String, Order])
