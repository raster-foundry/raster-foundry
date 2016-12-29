package com.azavea.rf.utils

import com.azavea.rf.auth.Authentication

trait RouterHelper extends Authentication
    with RfPaginationDirectives {

  val anonWithSlash = pathEndOrSingleSlash & authenticateAndAllowAnonymous
  val anonWithPage = anonWithSlash & withPagination
}
