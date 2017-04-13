package com.azavea.rf.api.utils

import io.circe._
import com.azavea.rf.common.cache.circe.auto._

case class ManagementBearerToken(access_token: String, expires_in: Int, token_type: String, scope: String)
