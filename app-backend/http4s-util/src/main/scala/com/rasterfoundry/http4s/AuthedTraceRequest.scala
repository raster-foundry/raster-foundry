package com.rasterfoundry.http4s

import com.colisweb.tracing.TracingContext
import com.rasterfoundry.datamodel.User
import org.http4s.AuthedRequest

/** Utility case class for combining tracing and authentication
  *
  * @param authedRequest
  * @param tracingContext
  * @tparam F
  */
case class AuthedTraceRequest[F[_]](authedRequest: AuthedRequest[F, User],
                                    tracingContext: TracingContext[F])
