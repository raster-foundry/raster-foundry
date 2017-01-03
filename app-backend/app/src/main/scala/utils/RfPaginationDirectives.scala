package com.azavea.rf.utils

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive1, Rejection}
import com.azavea.rf.datamodel.{PageRequest, Order}
import scala.collection.immutable.Seq

trait RfPaginationDirectives {

  lazy val OffsetParam = "page"
  lazy val LimitParam = "limit"
  lazy val SortParam = "sort"

  lazy val AscParam = "asc"
  lazy val DescParam = "desc"

  lazy val SortingSeparator = ";"
  lazy val OrderSeparator = ","

  lazy val DefaultOffsetParam = 0
  lazy val DefaultLimitParam = 10

  def withPagination: Directive1[PageRequest] = {
    parameterMap.flatMap { params =>
      (params.get(OffsetParam).map(_.toInt), params.get(LimitParam).map(_.toInt)) match {
        case (Some(offset), Some(limit)) if offset >= 0 => provide(deserializePage(offset, limit, params.get(SortParam)))
        case (Some(offset), Some(limit))                => provide(deserializePage(DefaultOffsetParam, limit, params.get(SortParam)))
        case (Some(offset), None) if offset >= 0        => provide(deserializePage(offset, DefaultLimitParam, params.get(SortParam)))
        case (Some(offset), None)                       => provide(deserializePage(DefaultOffsetParam, DefaultLimitParam, params.get(SortParam)))
        case (None, Some(limit))                        => provide(deserializePage(DefaultOffsetParam, limit, params.get(SortParam)))
        case (_, _)                                     => provide(deserializePage(DefaultOffsetParam, DefaultLimitParam, params.get(SortParam)))
      }
    }
  }

  def deserializePage(offset: Int, limit: Int, sorting: Option[String]) = {

    val sortingParam = sorting.map(_.split(SortingSeparator).map(_.span(_ != OrderSeparator)).collect {
      case (field, sort) if sort == ',' + AscParam  => (field, Order.Asc)
      case (field, sort) if sort == ',' + DescParam => (field, Order.Desc)
    }.toMap)

    PageRequest(offset, limit, sortingParam.getOrElse(Map.empty))
  }
}
