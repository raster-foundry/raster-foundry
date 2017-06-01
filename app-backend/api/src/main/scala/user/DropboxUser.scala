package com.azavea.rf.api.user

import io.circe.generic.JsonCodec

@JsonCodec
case class DropboxAuthRequest(authorizationCode: String)
