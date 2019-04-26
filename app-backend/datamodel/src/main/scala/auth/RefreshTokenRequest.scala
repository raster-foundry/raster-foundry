package com.rasterfoundry.datamodel.auth
import io.circe.generic.JsonCodec

@JsonCodec
final case class RefreshTokenRequest(grant_type: String,
                                     client_id: String,
                                     refresh_token: String)
