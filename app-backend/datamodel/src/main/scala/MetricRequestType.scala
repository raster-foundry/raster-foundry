package com.rasterfoundry.datamodel

sealed trait MetricRequestType

object MetricRequestType {
  case object ProjectMosaicRequest extends MetricRequestType
  case object AnalysisRequest extends MetricRequestType
}
