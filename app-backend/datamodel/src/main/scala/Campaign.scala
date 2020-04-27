package com.rasterfoundry.datamodel

import io.circe._
import io.circe.generic.semiauto._

import java.sql.Timestamp
import java.util.UUID

final case class Campaign(
    id: UUID,
    createdAt: Timestamp,
    owner: String,
    name: String,
    campaignType: AnnotationProjectType
)

object Campaign {
  implicit val encCampaign: Encoder[Campaign] = deriveEncoder
  implicit val decCampaignt: Decoder[Campaign] = deriveDecoder

  final case class Create(
      name: String,
      campaignType: AnnotationProjectType
  )

  object Create {
    implicit val decCreate: Decoder[Create] = deriveDecoder
  }
}
