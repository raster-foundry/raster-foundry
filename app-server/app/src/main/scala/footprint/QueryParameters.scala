package com.azavea.rf.footprint

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.ParameterDirectives.parameters

import com.azavea.rf.utils.queryparams._

case class FootprintQueryParameters(
  x: Option[Double],
  y: Option[Double],
  bbox: Option[String]
)

case class CombinedFootprintQueryParams(
  orgParams: OrgQueryParameters,
  timestampParams: TimestampQueryParameters,
  footprintParams: FootprintQueryParameters
)

trait FootprintQueryParameterDirective extends QueryParametersCommon {
  val footprintSpecificQueryParams = parameters(
    (
      'x.as[Double].?,
      'y.as[Double].?,
      'bbox.as[String].?
    )
  ).as(FootprintQueryParameters)

  val footprintQueryParameters = (
    orgQueryParams & timestampQueryParameters & footprintSpecificQueryParams
  ).as(CombinedFootprintQueryParams)
}
