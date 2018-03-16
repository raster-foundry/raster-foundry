package com.azavea.rf.datamodel

import akka.http.scaladsl.unmarshalling._

import io.circe._
import io.circe.generic.JsonCodec

@JsonCodec
case class License(
  short_name: String,
  name: String,
  url: String,
  osi_approved: Boolean
)
