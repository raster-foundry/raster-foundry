package com.rasterfoundry.batch.export.json

import com.rasterfoundry.datamodel._

import io.circe.generic.JsonCodec

import java.util.UUID

@JsonCodec
final case class S3ExportStatus(exportId: UUID, exportStatus: ExportStatus)
