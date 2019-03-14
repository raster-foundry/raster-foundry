package com.rasterfoundry.backsplash.server

import com.rasterfoundry.backsplash.OgcStore
import com.rasterfoundry.backsplash.OgcStore.ToOgcStoreOps
import com.rasterfoundry.backsplash.Parameters._
import com.rasterfoundry.common.datamodel.User

import cats.data.Validated
import cats.effect.IO
import geotrellis.server.ogc.wcs.params.{
  GetCapabilitiesWcsParams,
  WcsParams,
  WcsParamsError
}
import geotrellis.server.ogc.wcs.ops.Operations
import org.http4s._
import org.http4s.scalaxml._
import org.http4s.dsl.io._

class WcsService[LayerReader: OgcStore](layers: LayerReader)
    extends ToOgcStoreOps {

  private val urlPrefix = "https://tiles.staging.rasterfoundry.com/"

  private def requestToServiceUrl(request: Request[IO]) = {
    List(urlPrefix, request.scriptName, request.pathInfo).mkString("/")
  }

  // Authed so we can piggyback on magic public checks from existing authenticators,
  // and so that if something _can_ provide the params we want, we can still auth
  val routes = AuthedService[User, IO] {
    case authedReq @ GET -> Root / UUIDWrapper(projectId) as user =>
      val serviceUrl = requestToServiceUrl(authedReq.req)
      WcsParams(authedReq.req.multiParams) match {
        case Validated.Invalid(errors) =>
          BadRequest(
            s"Error parsing parameters: ${WcsParamsError.generateErrorMessage(errors.toList)}")

        case Validated.Valid(p) =>
          p match {
            case params: GetCapabilitiesWcsParams =>
              for {
                rsm <- layers.getModel(projectId)
                resp <- Ok(Operations.getCapabilities(serviceUrl, rsm, params))
              } yield resp
            case _ =>
              BadRequest("not yet implemented")
          }
      }
  }
}
