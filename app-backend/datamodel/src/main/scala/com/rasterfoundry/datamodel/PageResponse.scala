package com.rasterfoundry.datamodel
import scala.collection.immutable.Seq

case class PageResponse[T](elements: Seq[T], totalElements: Int)
