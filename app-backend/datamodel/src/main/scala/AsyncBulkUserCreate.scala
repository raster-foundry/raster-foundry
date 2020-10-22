package com.rasterfoundry.datamodel

import com.rasterfoundry.datamodel.newtypes.{AsyncJobErrors, CreatedUserIds}

import io.circe.generic.JsonCodec

import java.util.UUID

@JsonCodec final case class AsyncBulkUserCreate(
    id: UUID,
    owner: String,
    input: UserBulkCreate,
    status: AsyncJobStatus,
    errors: AsyncJobErrors,
    results: CreatedUserIds
)
