package com.azavea.rf.datamodel

import io.circe.generic.JsonCodec

import java.util.UUID

@JsonCodec
case class FeatureFlag(
  id: UUID,
  key: String,
  active: Boolean,
  name: String,
  description: String
)

object FeatureFlag {
  def tupled = (FeatureFlag.apply _).tupled
}
