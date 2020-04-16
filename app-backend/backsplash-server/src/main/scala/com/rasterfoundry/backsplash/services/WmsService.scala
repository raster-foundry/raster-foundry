package com.rasterfoundry.backsplash.server

import com.rasterfoundry.backsplash.OgcStore
import com.rasterfoundry.backsplash.OgcStore.ToOgcStoreOps
import com.rasterfoundry.backsplash.Parameters._
import com.rasterfoundry.backsplash.error._
import com.rasterfoundry.datamodel.User
import com.rasterfoundry.http4s.TracedHTTPRoutes
import com.rasterfoundry.http4s.TracedHTTPRoutes._

import cats.data.Validated._
import cats.effect.{ContextShift, IO}
import cats.implicits._
import com.colisweb.tracing.TracingContext
import com.colisweb.tracing.TracingContext.TracingContextBuilder
import com.typesafe.scalalogging.LazyLogging
import geotrellis.raster.{io => _, _}
import geotrellis.server._
import geotrellis.server.ogc._
import geotrellis.server.ogc.params.ParamError
import geotrellis.server.ogc.wms.{CapabilitiesView, WmsParams}
import io.circe.syntax._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.scalaxml._

import java.net.URL
import java.util.UUID

class WmsService[LayerReader: OgcStore](layers: LayerReader, urlPrefix: String)(
    implicit contextShift: ContextShift[IO],
    tracingContextBuilder: TracingContextBuilder[IO]
) extends ToOgcStoreOps
    with LazyLogging {

  private def requestToServiceUrl(request: Request[IO]) = {
    List(urlPrefix, request.scriptName, request.pathInfo).mkString
  }

  private def authedReqToResponse(
      authedReq: AuthedRequest[IO, User],
      projectId: UUID,
      serviceUrl: String,
      tracingContext: TracingContext[IO]
  ): IO[Response[IO]] =
    WmsParams(authedReq.req.multiParams) match {
      case Invalid(errors) =>
        BadRequest(s"Error parsing parameters: ${ParamError
          .generateErrorMessage(errors.toList)}")
      case Valid(p) =>
        p match {
          case _: WmsParams.GetCapabilities =>
            for {
              rsm <- layers.getWmsModel(projectId, tracingContext)
              resp <- Ok(new CapabilitiesView(rsm, new URL(serviceUrl)).toXML)
            } yield resp
          case params: WmsParams.GetMap =>
            val re =
              RasterExtent(params.boundingBox, params.width, params.height)
            for {
              rsm <- layers.getWmsModel(projectId, tracingContext)
              layer = rsm.getLayer(params).headOption getOrElse {
                params.layers.headOption match {
                  case None =>
                    throw RequirementFailedException(
                      "WMS Request must specify layers"
                    )
                  case Some(l) =>
                    throw RequirementFailedException(
                      s"Layer ${l} not found or something else went wrong"
                    )
                }
              }
              (evalExtent, evalHistogram) = layer match {
                case sl @ SimpleOgcLayer(_, title, _, _, _) =>
                  (
                    LayerExtent.identity(sl),
                    layers.getLayerHistogram(
                      UUID.fromString(title),
                      tracingContext
                    )
                  )
                case _: MapAlgebraOgcLayer =>
                  throw new MetadataException(
                    "Arbitrary MAML evaluation is not yet supported by backsplash's OGC endpoints"
                  )
              }
              respIO <- (evalExtent(re.extent, re.cellSize), evalHistogram)
                .parMapN {
                  case (Valid(mbTile), hists) =>
                    logger.debug(s"Style: ${layer.style}, hists are: ${hists}")
                    // We pad with this invisible tile to ensure tile sizes are as expected
                    // TODO: This really shouldn't be happening and appears not to be an issue
                    //  in gt-server itself. This resolves the issue
                    val invisiTile =
                      mbTile.prototype(params.width, params.height)
                    val fullTile = invisiTile merge mbTile
                    // TODO: The default coloring in RF should be handled by the default
                    //  color correction settings established elsewhere throughout the
                    //  platform
                    val tileResp = fullTile.bandCount match {
                      case 3 =>
                        Render.rgb(fullTile, layer.style, params.format, hists)
                      case 4 =>
                        Render.rgba(fullTile, layer.style, params.format, hists)
                      case _ =>
                        Render.singleband(
                          fullTile,
                          layer.style,
                          params.format,
                          hists
                        )
                    }
                    Ok(tileResp)
                  // at least one is invalid, we don't care which, and we want all the errors
                  // if both are
                  case (Invalid(errs), _) =>
                    // map from Validated[Errs, ?] to Validated[Errs, ()] for both, since we already
                    // know that at least one is invalid
                    BadRequest(errs asJson)
                }
              resp <- respIO
            } yield resp

          case _ =>
            BadRequest("not yet implemented")
        }
    }

  def routes: AuthedRoutes[User, IO] = TracedHTTPRoutes[IO] {
    case tracedReq @ GET -> Root / UUIDWrapper(projectId) as _ using tracingContext =>
      val serviceUrl = requestToServiceUrl(tracedReq.authedRequest.req)
      authedReqToResponse(
        tracedReq.authedRequest,
        projectId,
        serviceUrl,
        tracingContext
      )

    case tracedReq @ GET -> Root / UUIDWrapper(projectId) / "map-token" / UUIDWrapper(
          _
        ) as _ using tracingContext =>
      val serviceUrl = requestToServiceUrl(tracedReq.authedRequest.req)
      authedReqToResponse(
        tracedReq.authedRequest,
        projectId,
        serviceUrl,
        tracingContext
      )
  }
}
