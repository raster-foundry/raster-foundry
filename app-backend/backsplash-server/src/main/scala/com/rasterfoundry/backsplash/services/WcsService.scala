package com.rasterfoundry.backsplash.server

import com.rasterfoundry.backsplash.OgcStore
import com.rasterfoundry.backsplash.OgcStore.ToOgcStoreOps
import com.rasterfoundry.backsplash.Parameters._
import com.rasterfoundry.datamodel.User

import cats.data.Validated
import cats.effect.{ContextShift, IO}
import geotrellis.server.ogc.wcs.params.{
  DescribeCoverageWcsParams,
  GetCapabilitiesWcsParams,
  GetCoverageWcsParams,
  WcsParams,
  WcsParamsError
}
import geotrellis.server.ogc.wcs.ops.{GetCoverage, Operations}
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.scalaxml._

import com.typesafe.scalalogging.LazyLogging

import java.util.UUID

class WcsService[LayerReader: OgcStore](layers: LayerReader, urlPrefix: String)(
    implicit contextShift: ContextShift[IO])
    extends ToOgcStoreOps
    with LazyLogging {

  private def requestToServiceUrl(request: Request[IO]) = {
    List(urlPrefix, request.scriptName, request.pathInfo).mkString
  }

  private def authedReqToResponse(authedReq: AuthedRequest[IO, User],
                                  projectId: UUID,
                                  serviceUrl: String): IO[Response[IO]] =
    WcsParams(authedReq.req.multiParams) match {
      case Validated.Invalid(errors) =>
        BadRequest(
          s"Error parsing parameters: ${WcsParamsError.generateErrorMessage(errors.toList)}")

      case Validated.Valid(p) =>
        p match {
          case params: GetCapabilitiesWcsParams =>
            for {
              rsm <- layers.getWcsModel(projectId)
              resp <- Ok(Operations.getCapabilities(serviceUrl, rsm, params))
            } yield resp

          case params: DescribeCoverageWcsParams =>
            for {
              rsm <- layers.getWcsModel(projectId)
              resp <- Ok(Operations.describeCoverage(rsm, params))
            } yield {
              resp
            }

          case params: GetCoverageWcsParams =>
            for {
              rsm <- layers.getWcsModel(projectId)
              resp <- Ok(new GetCoverage(rsm).build(params))
            } yield resp

          case _ =>
            BadRequest("not yet implemented")
        }
    }

  // Authed so we can piggyback on magic public checks from existing authenticators,
  // and so that if something _can_ provide the params we want, we can still auth
  val routes: AuthedService[User, IO] = AuthedService[User, IO] {
    case authedReq @ GET -> Root / UUIDWrapper(projectId) as user =>
      val serviceUrl = requestToServiceUrl(authedReq.req)
      authedReqToResponse(authedReq, projectId, serviceUrl)

    case authedReq @ GET -> Root / UUIDWrapper(projectId) / "map-token" / UUIDWrapper(
          _) as user =>
      val serviceUrl = requestToServiceUrl(authedReq.req)
      authedReqToResponse(authedReq, projectId, serviceUrl)

    case r @ _ =>
      logger.warn(s"Unexpected request: ${r.req.pathInfo}, ${r.req.params}")
      NotFound()
  }
}
