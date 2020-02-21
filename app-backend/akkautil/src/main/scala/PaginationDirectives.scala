package com.rasterfoundry.akkautil

/**
  * Copied from https://github.com/lonelyplanet/akka-http-extensions
  * with minor modifications for removing unused variables/code-paths
  */
import com.rasterfoundry.datamodel._

import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives._

trait PaginationDirectives {

  private lazy val OffsetParam = "page"
  private lazy val LimitParam = "pageSize"
  private lazy val SortParam = "sort"
  private lazy val AscParam = "asc"
  private lazy val DescParam = "desc"

  private lazy val SortingSeparator = ";"
  private lazy val OrderSeparator = ','

  private lazy val DefaultOffsetParam = 0
  private lazy val DefaultLimitParam = 30

  /**
    * Always returns a PageRequest
    * If values are passed as part of HTTP request, they are taken from it
    * If not, (default) values are read from configuration
    *
    * @return PageRequest - taken from HTTP request or from configuration defaults
    */
  def withPagination: Directive1[PageRequest] = {
    parameterMap.flatMap { params =>
      (params.get(OffsetParam).map(_.toInt),
       params.get(LimitParam).map(_.toInt)) match {
        case (Some(offset), Some(limit)) =>
          provide(deserializePage(offset, limit, params.get(SortParam)))
        case (Some(offset), None) =>
          provide(
            deserializePage(offset, DefaultLimitParam, params.get(SortParam)))
        case (None, Some(limit)) =>
          provide(
            deserializePage(DefaultOffsetParam, limit, params.get(SortParam)))
        case (_, _) =>
          provide(
            deserializePage(DefaultOffsetParam,
                            DefaultLimitParam,
                            params.get(SortParam)))
      }
    }
  }

  private def deserializePage(offset: Int,
                              limit: Int,
                              sorting: Option[String]) = {

    val sortingParam = sorting.map(
      _.split(SortingSeparator)
        .map(_.span(_ != OrderSeparator))
        .collect {
          case (field, sort) if sort == ',' + AscParam  => (field, Order.Asc)
          case (field, sort) if sort == ',' + DescParam => (field, Order.Desc)
        }
        .toMap)

    PageRequest(offset, limit, sortingParam.getOrElse(Map.empty))
  }
}
