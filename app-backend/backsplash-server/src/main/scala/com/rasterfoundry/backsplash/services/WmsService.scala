package com.rasterfoundry.backsplash.server

import com.rasterfoundry.backsplash.error._
import com.rasterfoundry.backsplash.OgcStore
import com.rasterfoundry.backsplash.OgcStore.ToOgcStoreOps
import com.rasterfoundry.backsplash.Parameters._
import com.rasterfoundry.common.datamodel.User

import cats.data.Validated._
import cats.effect.{ContextShift, IO}
import cats.implicits._
import com.azavea.maml.eval.Interpreter
import io.circe.syntax._
import geotrellis.raster._
import geotrellis.server._
import geotrellis.server.ogc._
import geotrellis.server.ogc.params.ParamError
import geotrellis.server.ogc.wms.{CapabilitiesView, WmsParams}
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.scalaxml._

import com.typesafe.scalalogging.LazyLogging

import java.net.URL
import java.util.UUID

class WmsService[LayerReader: OgcStore](layers: LayerReader, urlPrefix: String)(
    implicit contextShift: ContextShift[IO])
    extends ToOgcStoreOps
    with LazyLogging {

  private def requestToServiceUrl(request: Request[IO]) = {
    List(urlPrefix, request.scriptName, request.pathInfo).mkString
  }

  def routes: AuthedService[User, IO] = AuthedService[User, IO] {
    case authedReq @ GET -> Root / UUIDWrapper(projectId) as user =>
      val serviceUrl = requestToServiceUrl(authedReq.req)
      WmsParams(authedReq.req.multiParams) match {
        case Invalid(errors) =>
          BadRequest(s"Error parsing parameters: ${ParamError
            .generateErrorMessage(errors.toList)}")
        case Valid(p) =>
          p match {
            case params: WmsParams.GetCapabilities =>
              for {
                rsm <- layers.getWmsModel(projectId)
                metadata <- layers.getWmsServiceMetadata(projectId)
                resp <- Ok(new CapabilitiesView(rsm, new URL(serviceUrl)).toXML)
              } yield resp
            case params: WmsParams.GetMap =>
              val re =
                RasterExtent(params.boundingBox, params.width, params.height)
              for {
                rsm <- layers.getWmsModel(projectId)
                layer = rsm.getLayer(params.crs,
                                     params.layers.headOption,
                                     params.styles.headOption) getOrElse {
                  params.layers.headOption match {
                    case None =>
                      throw RequirementFailedException(
                        "WMS Request must specify layers")
                    case Some(l) =>
                      throw RequirementFailedException(
                        s"Layer ${l} not found or something else went wrong")
                  }
                }
                (evalExtent, evalHistogram) = layer match {
                  case sl @ SimpleOgcLayer(_, title, _, _, _) =>
                    (LayerExtent.identity(sl),
                     layers.getLayerHistogram(UUID.fromString(title)))
                  case MapAlgebraOgcLayer(_, _, _, parameters, expr, _) =>
                    throw MetadataException("Arbitrary MAML evaluation is not yet supported by backsplash's OGC endpoints")
                }
                respIO <- (evalExtent(re.extent, re.cellSize), evalHistogram)
                  .parMapN {
                    case (Valid(mbTile), hists) =>
                      logger.debug(
                        s"Style: ${layer.style}, hists are: ${hists}")
                      val tileResp = layer.style map {
                        _.renderImage(mbTile, params.format, hists)
                      } getOrElse {
                        Render(mbTile, layer.style, params.format, hists)
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
