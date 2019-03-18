package com.rasterfoundry.backsplash.server

import com.rasterfoundry.backsplash.OgcStore
import com.rasterfoundry.backsplash.OgcStore.ToOgcStoreOps
import com.rasterfoundry.backsplash.Parameters._
import com.rasterfoundry.common.datamodel.User

import cats.data.Validated
import cats.effect.IO
import geotrellis.proj4.WebMercator
import geotrellis.server.ogc.params.ParamError
import geotrellis.server.ogc.wms.{CapabilitiesView, WmsParams}
import org.http4s._
import org.http4s.scalaxml._
import org.http4s.dsl.io._

import java.net.URL

class WmsService[LayerReader: OgcStore](layers: LayerReader)
    extends ToOgcStoreOps {

  private val urlPrefix = "https://tiles.staging.rasterfoundry.com/"

  private def requestToServiceUrl(request: Request[IO]) = {
    List(urlPrefix, request.scriptName, request.pathInfo).mkString("/")
  }

  def routes = AuthedService[User, IO] {
    case authedReq @ GET -> Root / UUIDWrapper(projectId) as user =>
      val serviceUrl = requestToServiceUrl(authedReq.req)
      WmsParams(authedReq.req.multiParams) match {
        case Validated.Invalid(errors) =>
          BadRequest(s"Error parsing parameters: ${ParamError
            .generateErrorMessage(errors.toList)}")
        case Validated.Valid(p) =>
          p match {
            case params: WmsParams.GetCapabilities =>
              for {
                rsm <- layers.getModel(projectId)
                metadata <- layers.getWmsServiceMetadata(projectId)
                resp <- Ok(
                  new CapabilitiesView(rsm,
                                       new URL(serviceUrl),
                                       metadata,
                                       defaultCrs = WebMercator).toXML)
              } yield resp
            case _ =>
              BadRequest("not yet implemented")
          }
      }

    case r @ GET -> Root / UUIDWrapper(_) / "map-token" / UUIDWrapper(_) as user =>
      logger.debug(s"Request path info to start: ${r.req.pathInfo}")
      val rewritten = OgcMapTokenRewrite(r)
      logger.debug(
        s"Request path info after rewrite: ${rewritten.req.pathInfo}")
      routes(rewritten).value flatMap {
        case Some(resp) =>
          IO.pure { resp }
        case _ =>
          NotFound()
      }
  }
}
