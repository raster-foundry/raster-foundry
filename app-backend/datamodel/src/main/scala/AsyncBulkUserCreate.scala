package com.rasterfoundry.datamodel

import java.util.UUID
import io.circe.generic.JsonCodec

@JsonCodec final case class AsyncBulkUserCreate(
    id: UUID,
    owner: String,
    input: UserBulkCreate,
    status: AsyncJobStatus,
    errors: List[String],
    results: List[UserWithCampaign]
)
