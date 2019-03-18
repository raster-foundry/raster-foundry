package com.rasterfoundry.backsplash.server

import com.rasterfoundry.backsplash.Parameters.UUIDWrapper

import org.http4s._
import org.http4s.dsl.io._

import com.typesafe.scalalogging.LazyLogging

object OgcMapTokenRewrite extends LazyLogging {
  private def sanitize(requestBase: String): Uri = {
    val safePath =
      requestBase.replace("/wcs", "").replace("/wms", "").stripSuffix("/")
    Uri.fromString(safePath).right.get
  }

  def apply[F[_], U](authedReq: AuthedRequest[F, U]): AuthedRequest[F, U] = {
    val basePath = authedReq.req.pathInfo
    // getting autoslash and this request rewrite to play together nicely has been a
    // challenge, so brute force the ordering by autoslashing here, basically
    val req =
      authedReq.req.withPathInfo(authedReq.req.pathInfo.stripSuffix("/"))
    if (basePath.contains("map-token")) {
      req match {
        case _ -> prior / "map-token" / UUIDWrapper(mapTokenId) =>
          logger.info("Translating uri to move map token to qp")
          val newUri = sanitize(s"$prior")
          logger.debug(s"New uri after path replacement: $newUri")
          val withParams =
            newUri.setQueryParams(
              Map(
                ("mapToken" -> Seq(mapTokenId.toString)) +:
                  req.multiParams.toSeq: _*))

          logger.debug(s"New uri after param replacement: $withParams")
          val newRequest =
            req.withUri(withParams)
          logger.debug(s"Path info of new request: ${req.pathInfo}")
          authedReq.copy(req = newRequest)
        case _ =>
          authedReq
      }
    } else {
      authedReq
    }
  }
}
