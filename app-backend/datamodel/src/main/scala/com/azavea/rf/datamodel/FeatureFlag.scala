package com.azavea.rf.datamodel

import java.util.UUID

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
