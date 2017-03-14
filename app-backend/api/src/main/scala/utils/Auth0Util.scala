package com.azavea.rf.api.utils

import io.circe._
import io.circe.generic.auto._

case class ManagementBearerToken(access_token: String, expires_in: Int, token_type: String, scope: String)
