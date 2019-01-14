package com.rasterfoundry.backsplash.server

import org.http4s._
import org.http4s.server._
import org.http4s.server.middleware._

import cats._
import cats.data._
import cats.implicits._

/** Removes the given prefix from the beginning of the path of the [[Request]].
  */
object AuthedTranslateUri {

  def apply[F[_], T](prefix: String)(http: AuthedService[T, F])(
      implicit F: MonoidK[OptionT[F, ?]],
      ev: Functor[F]): AuthedService[T, F] =
    if (prefix.isEmpty || prefix == "/") http
    else {
      val (slashedPrefix, unslashedPrefix) =
        if (prefix.startsWith("/")) (prefix, prefix.substring(1))
        else (s"/$prefix", prefix)

      val newCaret = slashedPrefix.length

      Kleisli { authedReq =>
        val shouldTranslate =
          authedReq.req.pathInfo
            .startsWith(unslashedPrefix) || authedReq.req.pathInfo
            .startsWith(slashedPrefix)

        if (shouldTranslate)
          http(
            AuthedRequest(authedReq.authInfo,
                          setCaret(authedReq.req, newCaret)))
        else F.empty
      }

    }

  private def setCaret[F[_]: Functor](req: Request[F],
                                      newCaret: Int): Request[F] = {
    val oldCaret = req.attributes
      .get(Request.Keys.PathInfoCaret)
      .getOrElse(0)
    req.withAttribute(Request.Keys.PathInfoCaret(oldCaret + newCaret))
  }
}
