package com.rasterfoundry.datamodel

import cats.implicits._
import io.circe._
import io.circe.generic.semiauto._
import io.circe.syntax._

import java.util.UUID

sealed trait MetricEvent {
  def toQueryParams: MetricQueryParameters
  val referer: String
}

object MetricEvent {
  import AnalysisEvent._
  import ProjectLayerMosaicEvent._
  implicit val encMetricEvent: Encoder[MetricEvent] = new Encoder[MetricEvent] {
    def apply(event: MetricEvent) = event match {
      case plm: ProjectLayerMosaicEvent => plm.asJson
      case analysis: AnalysisEvent      => analysis.asJson
    }
  }
  implicit val decMetricEvent: Decoder[MetricEvent] = Decoder[
    ProjectLayerMosaicEvent].widen or Decoder[AnalysisEvent].widen
}

final case class ProjectLayerMosaicEvent(
    projectId: UUID,
    projectLayerId: UUID,
    projectOwner: String,
    referer: String
) extends MetricEvent {
  def toQueryParams = MetricQueryParameters(
    projectId = Some(projectId),
    projectLayerId = Some(projectLayerId),
    referer = Some(referer),
    requestType = MetricRequestType.ProjectMosaicRequest
  )
}

object ProjectLayerMosaicEvent {
  implicit val decProjectLayerMosaicEvent: Decoder[ProjectLayerMosaicEvent] =
    deriveDecoder
  implicit val encProjectLayerMosaicEvent: Encoder[ProjectLayerMosaicEvent] =
    deriveEncoder
}

final case class AnalysisEvent(
    projectId: Option[UUID],
    projectLayerId: Option[UUID],
    analysisId: UUID,
    nodeId: Option[UUID],
    analysisOwner: String,
    referer: String
) extends MetricEvent {
  def toQueryParams = MetricQueryParameters(
    analysisId = Some(analysisId),
    nodeId = nodeId,
    referer = Some(referer),
    requestType = MetricRequestType.AnalysisRequest
  )
}

object AnalysisEvent {
  implicit val decAnalysisEvent: Decoder[AnalysisEvent] = deriveDecoder
  implicit val encAnalysisEvent: Encoder[AnalysisEvent] = deriveEncoder
}
