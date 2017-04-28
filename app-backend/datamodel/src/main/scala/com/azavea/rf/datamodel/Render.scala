package com.azavea.rf.datamodel

import io.circe.generic.JsonCodec

@JsonCodec
case class Render(operation: String, bands: Option[Seq[Int]])
