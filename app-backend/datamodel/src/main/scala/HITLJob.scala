package com.rasterfoundry.datamodel

import io.circe._
import io.circe.generic.JsonCodec
import io.circe.generic.semiauto._

import java.sql.Timestamp
import java.util.UUID

@JsonCodec
final case class HITLJob(
    id: UUID,
    createdAt: Timestamp,
    createdBy: String,
    modifiedAt: Timestamp,
    owner: String,
    campaignId: UUID,
    projectId: UUID,
    status: HITLJobStatus,
    version: Int
)

object HITLJob {

  def tupled = (HITLJob.apply _).tupled

  final case class Create(
      campaignId: UUID,
      projectId: UUID,
      status: HITLJobStatus
  )

  object Create {
    implicit val decCreate: Decoder[Create] = deriveDecoder
  }

}
