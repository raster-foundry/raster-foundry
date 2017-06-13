package com.azavea.rf.batch.export.json

import com.azavea.rf.datamodel._
import io.circe.generic.JsonCodec

import java.util.UUID

@JsonCodec
case class S3ExportStatus(exportId: UUID, exportStatus: ExportStatus)
