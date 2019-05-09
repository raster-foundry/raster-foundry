package com.rasterfoundry.datamodel.auth

import io.circe.generic.JsonCodec

@JsonCodec
final case class AuthorizedToken(id_token: String,
                                 access_token: String,
                                 expires_in: Int,
                                 token_type: String)
