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
    campaignType: AnnotationProjectType,
    description: Option[String] = None,
    videoLink: Option[String] = None,
    partnerName: Option[String] = None,
    partnerLogo: Option[String] = None
)

object Campaign {
  implicit val encCampaign: Encoder[Campaign] = deriveEncoder
  implicit val decCampaign: Decoder[Campaign] = deriveDecoder

  final case class Create(
      name: String,
      campaignType: AnnotationProjectType,
      description: Option[String] = None,
      videoLink: Option[String] = None,
      partnerName: Option[String] = None,
      partnerLogo: Option[String] = None
  )

  object Create {
    implicit val decCreate: Decoder[Create] = deriveDecoder
  }
}
