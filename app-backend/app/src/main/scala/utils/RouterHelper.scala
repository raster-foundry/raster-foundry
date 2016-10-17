package com.azavea.rf.utils

import com.lonelyplanet.akka.http.extensions.PaginationDirectives
import com.azavea.rf.auth.Authentication

trait RouterHelper extends Authentication
    with PaginationDirectives {

  val anonWithSlash = pathEndOrSingleSlash & authenticateAndAllowAnonymous
  val anonWithPage = anonWithSlash & withPagination
}
