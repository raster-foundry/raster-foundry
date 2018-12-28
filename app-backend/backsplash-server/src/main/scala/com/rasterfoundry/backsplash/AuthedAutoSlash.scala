package com.rasterfoundry.backsplash.server

import cats._
import cats.data._
import cats.implicits._
import org.http4s._
import org.http4s.server._
import org.http4s.server.middleware._

/** Removes a trailing slash from [[Request]] path
  *
  * If a route exists with a file style [[Uri]], eg "/foo",
  * this middleware will cause [[Request]]s with uri = "/foo" and
  * uri = "/foo/" to match the route.
  */
object AuthedAutoSlash {
  def apply[T, F[_]](http: AuthedService[T, F])(
      implicit F: MonoidK[OptionT[F, ?]]): AuthedService[T, F] = Kleisli {
    authedReq =>
      {
        http(authedReq) <+> {
          val pathInfo = authedReq.req.pathInfo

          if (pathInfo.isEmpty || pathInfo.charAt(pathInfo.length - 1) != '/') {
            F.empty
          } else {
            // Request has not been translated already
            http.apply(
              authedReq.copy(req = authedReq.req.withPathInfo(
                pathInfo.substring(0, pathInfo.length - 1))))
          }
        }
      }
  }
}
