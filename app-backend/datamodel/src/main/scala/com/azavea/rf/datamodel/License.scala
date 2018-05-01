package com.azavea.rf.datamodel

import akka.http.scaladsl.unmarshalling._

import io.circe._
import io.circe.generic.JsonCodec

@JsonCodec
case class License(
  shortName: String,
  name: String,
  url: String,
  osiApproved: Boolean
)