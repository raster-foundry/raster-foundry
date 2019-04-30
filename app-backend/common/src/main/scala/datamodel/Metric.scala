package com.rasterfoundry.common.datamodel

import io.circe._
import io.circe.generic.semiauto._

import java.time.{Instant, LocalDateTime, LocalDate, ZoneOffset}
import java.util.UUID

case class Metric(
    id: UUID,
    period: (LocalDate, LocalDate),
    metricEvent: MetricEvent,
    value: Int,
    requester: String
)

object Metric {

  implicit val encMetric: Encoder[Metric] = deriveEncoder[Metric]

  def apply(id: UUID,
            metricEvent: MetricEvent,
            occurredAt: Instant,
            value: Int,
            requester: String): Metric =
    Metric(
      id,
      rangeForInstant(occurredAt),
      metricEvent,
      value,
      requester
    )

  def rangeForInstant(instant: Instant): (LocalDate, LocalDate) = {
    val dayStart =
      LocalDateTime.ofInstant(instant, ZoneOffset.UTC).toLocalDate
    (dayStart, dayStart.plusDays(1))
  }
}
