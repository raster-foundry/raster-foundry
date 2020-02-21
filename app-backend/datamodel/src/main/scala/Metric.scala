package com.rasterfoundry.datamodel

import io.circe._
import io.circe.generic.semiauto._

import java.time.{Instant, LocalDate, LocalDateTime, ZoneOffset}

final case class Metric(
    period: (LocalDate, LocalDate),
    metricEvent: MetricEvent,
    requester: String,
    value: Int
)

object Metric {

  implicit val encMetric: Encoder[Metric] = deriveEncoder[Metric]

  def apply(occurredAt: Instant,
            metricEvent: MetricEvent,
            requester: String): Metric =
    Metric(
      rangeForInstant(occurredAt),
      metricEvent,
      requester,
      1
    )

  def rangeForInstant(instant: Instant): (LocalDate, LocalDate) = {
    val dayStart =
      LocalDateTime.ofInstant(instant, ZoneOffset.UTC).toLocalDate
    (dayStart, dayStart.plusDays(1))
  }
}
