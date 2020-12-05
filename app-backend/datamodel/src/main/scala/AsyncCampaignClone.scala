package com.rasterfoundry.datamodel

import com.rasterfoundry.datamodel.newtypes._

import io.circe.generic.JsonCodec

import java.{util => ju}

@JsonCodec final case class AsyncCampaignClone(
    id: ju.UUID,
    owner: String,
    input: Campaign.Clone,
    status: AsyncJobStatus,
    errors: AsyncJobErrors,
    results: Option[CampaignResult]
)
