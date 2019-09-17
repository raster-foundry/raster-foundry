package com.rasterfoundry.http4s.xray

import io.circe.generic.JsonCodec

@JsonCodec
final case class XrayResponse(status: Integer, content_length: Integer)
