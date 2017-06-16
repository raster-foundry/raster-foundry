package com.azavea.rf.batch.ingest.json

import java.util.UUID

import com.azavea.rf.datamodel._
import io.circe.generic.JsonCodec

@JsonCodec
case class S3IngestStatus(sceneId: UUID, ingestStatus: IngestStatus)

