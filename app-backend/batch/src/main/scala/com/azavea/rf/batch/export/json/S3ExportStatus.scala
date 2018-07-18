package com.azavea.rf.batch.export.json

import java.util.UUID

import com.azavea.rf.datamodel._
import io.circe.generic.JsonCodec

@JsonCodec
final case class S3ExportStatus(exportId: UUID, exportStatus: ExportStatus)
