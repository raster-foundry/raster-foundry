package com.azavea.rf.datamodel

import java.util.UUID
import java.sql.Timestamp

import io.circe._
import io.circe.generic.JsonCodec
import io.circe.syntax._

@JsonCodec
case class Platform(
  id: UUID,
  name: String,
  settings: Json,
  isActive: Boolean,
  defaultOrganizationId: Option[UUID]
)
