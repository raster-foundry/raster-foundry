package com.azavea.rf.datamodel

import io.circe._

import java.util.UUID
import java.sql.Timestamp

import io.circe.generic.JsonCodec

@JsonCodec
case class WorkspaceAnalysis(
  workspaceId: UUID,
  analysisId: UUID
)
