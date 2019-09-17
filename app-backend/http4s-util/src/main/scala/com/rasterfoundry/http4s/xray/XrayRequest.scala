package com.rasterfoundry.http4s.xray

import io.circe.generic.JsonCodec

@JsonCodec
final case class XrayRequest(method: String,
                             url: String,
                             user_agent: Option[String],
                             client_ip: Option[String])
