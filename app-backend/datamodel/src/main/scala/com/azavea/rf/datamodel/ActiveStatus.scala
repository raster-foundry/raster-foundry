package com.azavea.rf.datamodel

import io.circe._
import io.circe.generic.JsonCodec
import io.circe.syntax._

@JsonCodec
case class ActiveStatus(
  isActive: Boolean
)
