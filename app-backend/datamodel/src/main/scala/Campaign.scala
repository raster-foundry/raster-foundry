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
    projectStatuses: Map[String, Int],
    isActive: Boolean,
    resourceLink: Option[String] = None,
    taskStatusSummary: Map[String, Int],
    imageCount: Int = 0
) {
  def withRelated(
      labelClassGroups: List[AnnotationLabelClassGroup.WithLabelClasses]
  ): Campaign.WithRelated = {
    Campaign.WithRelated(
      id,
      createdAt,
      owner,
      name,
      campaignType,
      description,
      videoLink,
      partnerName,
      partnerLogo,
      parentCampaignId,
      continent,
      tags,
      childrenCount,
      projectStatuses,
      isActive,
      resourceLink,
      labelClassGroups,
      taskStatusSummary,
      imageCount
    )
  }
}

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
      tags: List[String] = List.empty,
      resourceLink: Option[String] = None
  )

  object Create {
    implicit val decCreate: Decoder[Create] = deriveDecoder
    implicit val encCreate: Encoder[Create] = deriveEncoder
  }

  final case class Clone(
      tags: List[String] = List.empty,
      grantAccessToParentCampaignOwner: Boolean = false,
      copyResourceLink: Boolean = false
  )

  object Clone {
    implicit val decClone: Decoder[Clone] = deriveDecoder
    implicit val encClone: Encoder[Clone] = deriveEncoder
  }

  final case class WithRelated(
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
      projectStatuses: Map[String, Int],
      isActive: Boolean,
      resourceLink: Option[String] = None,
      labelClassGroups: List[AnnotationLabelClassGroup.WithLabelClasses],
      taskStatusSummary: Map[String, Int],
      imageCount: Int = 0
  )

  object WithRelated {
    implicit val encRelated: Encoder[WithRelated] = deriveEncoder
    implicit val decRelated: Decoder[WithRelated] = deriveDecoder
  }
}
