package com.rasterfoundry.common.datamodel

import cats.implicits._
import io.circe._
import io.circe.syntax._
import io.circe.generic.semiauto._

import java.util.UUID

sealed trait MetricEvent {
  def toQueryParams: MetricQueryParameters
}

object MetricEvent {
  import ProjectLayerMosaicEvent._
  import AnalysisEvent._
  implicit val encMetricEvent: Encoder[MetricEvent] = new Encoder[MetricEvent] {
    def apply(event: MetricEvent) = event match {
      case plm: ProjectLayerMosaicEvent => plm.asJson
      case analysis: AnalysisEvent      => analysis.asJson
    }
  }
  implicit val decMetricEvent: Decoder[MetricEvent] = Decoder[
    ProjectLayerMosaicEvent].widen or Decoder[AnalysisEvent].widen
}

case class ProjectLayerMosaicEvent(
    projectId: UUID,
    projectLayerId: UUID,
    projectOwner: String,
    isAnalysis: Boolean = false
) extends MetricEvent {
  def toQueryParams = MetricQueryParameters(
    projectId = Some(projectId),
    projectLayerId = Some(projectLayerId),
    requestType = MetricRequestType.ProjectMosaicRequest
  )
}

object ProjectLayerMosaicEvent {
  implicit val decProjectLayerMosaicEvent: Decoder[ProjectLayerMosaicEvent] =
    deriveDecoder
  implicit val encProjectLayerMosaicEvent: Encoder[ProjectLayerMosaicEvent] =
    deriveEncoder
}

case class AnalysisEvent(
    projectId: Option[UUID],
    projectLayerId: Option[UUID],
    analysisId: UUID,
    nodeId: Option[UUID],
    analysisOwner: String,
    isAnalysis: Boolean = true
) extends MetricEvent {
  def toQueryParams = MetricQueryParameters(
    analysisId = Some(analysisId),
    nodeId = nodeId,
    requestType = MetricRequestType.AnalysisRequest
  )
}

object AnalysisEvent {
  implicit val decAnalysisEvent: Decoder[AnalysisEvent] = deriveDecoder
  implicit val encAnalysisEvent: Encoder[AnalysisEvent] = deriveEncoder
}
