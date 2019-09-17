package com.rasterfoundry.http4s.xray

import io.circe.generic.JsonCodec

@JsonCodec
final case class XrayHttp(
    request: Option[XrayRequest],
    response: Option[XrayResponse]
)
