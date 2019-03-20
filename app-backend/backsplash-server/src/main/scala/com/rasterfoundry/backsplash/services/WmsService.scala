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
                  case sl @ SimpleOgcLayer(_, _, _, _, _) =>
                    (LayerExtent.identity(sl), LayerHistogram.identity(sl, 512))
                  case MapAlgebraOgcLayer(_, _, _, parameters, expr, _) =>
                    (LayerExtent(IO.pure(expr),
                                 IO.pure(parameters),
                                 Interpreter.DEFAULT),
                     LayerHistogram(IO.pure(expr),
                                    IO.pure(parameters),
                                    Interpreter.DEFAULT,
                                    512))
                }
                respIO <- (evalExtent(re.extent, re.cellSize), evalHistogram)
                  .parMapN {
                    case (Valid(mbTile), Valid(hists)) =>
                      logger.debug("Got some valid stuff")
                      logger.debug(
                        s"Style: ${layer.style}, hists are: ${hists}")
                      Ok(Render(mbTile, layer.style, params.format, hists))
                    // at least one is invalid, we don't care which, and we want all the errors
                    // if both are
                    case (a, b) =>
                      // map from Validated[Errs, ?] to Validated[Errs, ()] for both, since we already
                      // know that at least one is invalid
                      (a map { _ =>
                        ()
                      }) combine (b map { _ =>
                        ()
                      }) match {
                        case Invalid(errs) =>
                          BadRequest(errs asJson)
                        case _ =>
                          BadRequest("compiler can't tell this is unreachable")
                      }
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
      logger.debug(s"Request path info after rewrite: ${rewritten.req.pathInfo}")
      routes(rewritten).value flatMap {
        case Some(resp) =>
          IO.pure { resp }
        case _ =>
          NotFound()
      }
  }
}
