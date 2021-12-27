package com.rasterfoundry.http4s

import com.rasterfoundry.datamodel.UserWithPlatform

import com.colisweb.tracing.core.TracingContext
import org.http4s.AuthedRequest

/** Utility case class for combining tracing and authentication
  *
  * @param authedRequest
  * @param tracingContext
  * @tparam F
  */
case class AuthedTraceRequest[F[_]](
    authedRequest: AuthedRequest[F, UserWithPlatform],
    tracingContext: TracingContext[F]
)
