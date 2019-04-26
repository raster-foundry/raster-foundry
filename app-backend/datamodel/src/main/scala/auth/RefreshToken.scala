package com.rasterfoundry.datamodel.auth
import io.circe.generic.JsonCodec

// TODO: this sort of case class definition should live in datamodel
@JsonCodec
final case class RefreshToken(refresh_token: String)
