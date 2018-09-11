package com.azavea.rf.datamodel

import io.circe.generic.JsonCodec
import io.circe.generic.semiauto._

case class HistogramAttribute(
    buckets: List[(Float, Int)],
    maximum: Float,
    minimum: Float,
    maxBucketCount: Int
)

object HistogramAttribute {
  implicit def decoderHistogramAttribute = deriveDecoder[HistogramAttribute]
  implicit def encoderHistogramAttribute = deriveEncoder[HistogramAttribute]
}
