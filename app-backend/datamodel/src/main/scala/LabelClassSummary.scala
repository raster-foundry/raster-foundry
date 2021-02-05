package com.rasterfoundry.datamodel

import io.circe._
import io.circe.generic.semiauto._
import java.util.UUID

final case class LabelClassSummary(
    labelClassId: UUID,
    labelClassName: String,
    count: Int
)

object LabelClassSummary {
  implicit val encLabelClassGroupSummary: Encoder[LabelClassSummary] =
    deriveEncoder
}

final case class LabelClassGroupSummary(
    labelClassGroupId: UUID,
    labelClassGroupName: String,
    labelClassSummaries: List[LabelClassSummary]
)

object LabelClassGroupSummary {
  implicit val encLabelClassGroupSummary: Encoder[LabelClassGroupSummary] =
    deriveEncoder
}
