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
    partnerLogo: Option[String] = None,
    parentCampaignId: Option[UUID] = None,
    continent: Option[Continent] = None,
    tags: List[String] = List.empty,
    childrenCount: Int,
    status: Map[String, Int]
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
      partnerLogo: Option[String] = None,
      parentCampaignId: Option[UUID] = None,
      continent: Option[Continent] = None,
      tags: List[String] = List.empty
  )

  object Create {
    implicit val decCreate: Decoder[Create] = deriveDecoder
  }

  final case class Clone(
      tags: List[String] = List.empty
  )

  object Clone {
    implicit val decClone: Decoder[Clone] = deriveDecoder
  }
}
