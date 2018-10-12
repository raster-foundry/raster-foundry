package com.rasterfoundry.batch.export.json

import java.util.UUID

import com.rasterfoundry.datamodel._
import io.circe.generic.JsonCodec

@JsonCodec
final case class S3ExportStatus(exportId: UUID, exportStatus: ExportStatus)
