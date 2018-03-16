package com.azavea.rf.datamodel

import java.util.UUID

case class OrgFeatures(
  organization: UUID,
  featureFlag: UUID,
  active: Boolean
)

object OrgFeatures {
  def tupled = (OrgFeatures.apply _).tupled
}
