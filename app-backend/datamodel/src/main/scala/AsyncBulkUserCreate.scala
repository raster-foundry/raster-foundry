package com.rasterfoundry.datamodel

import io.circe.generic.JsonCodec

import java.util.UUID

@JsonCodec final case class AsyncBulkUserCreate(
    id: UUID,
    owner: String,
    input: UserBulkCreate,
    status: AsyncJobStatus,
    errors: List[String],
    results: List[UserWithCampaign]
)
