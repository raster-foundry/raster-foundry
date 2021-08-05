package com.rasterfoundry.backsplash.server

import com.rasterfoundry.backsplash.Parameters._
import com.rasterfoundry.backsplash._
import com.rasterfoundry.common.utils.TileUtils
import com.rasterfoundry.http4s.TracedHTTPRoutes
import com.rasterfoundry.http4s.TracedHTTPRoutes._

import cats.data.Validated._
import cats.effect.{ContextShift, IO}
import cats.implicits._
import com.azavea.maml.ast.{GeomLit, Masking, RasterVar}
import com.azavea.maml.eval.ConcurrentInterpreter
import com.colisweb.tracing.core.TracingContextBuilder
import doobie.util.transactor.Transactor
import geotrellis.server._
import geotrellis.vector.io.json.Implicits._
import geotrellis.vector.{Polygon, Projected}
import io.circe.parser._
import io.circe.syntax._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.headers._

import java.util.UUID
import com.azavea.maml.ast.Expression
import io.chrisdavenport.log4cats.Logger

class MosaicService[LayerStore: RenderableStore, HistStore, ToolStore](
    layers: LayerStore,
    mosaicImplicits: MosaicImplicits[HistStore],
    analysisManager: AnalysisManager[ToolStore, HistStore],
    xa: Transactor[IO],
    contextShift: ContextShift[IO]
)(implicit tracingContext: TracingContextBuilder[IO], logger: Logger[IO]) {

  implicit val cs = contextShift

  import mosaicImplicits._

  implicit val projectLayerCache = Cache.caffeineProjectLayerCache

  implicit val tmsReification = paintedMosaicTmsReification

  private val pngType = `Content-Type`(MediaType.image.png)

  val authorizers = new Authorizers(xa)

  val routes =
    TracedHTTPRoutes[IO] {
      case GET -> Root / UUIDWrapper(projectId) / "layers" / UUIDWrapper(
            layerId
          ) / IntVar(z) / IntVar(x) / IntVar(
            y
          ) :? BandOverrideQueryParamDecoder(
            bandOverride
          ) :? DisableAutoCorrectionQueryParamDecoder(
            disableColorCorrect
          ) :? ColorCorrectLowerQuantile(
            lowerQuantile
          ) :? ColorCorrectUpperQuantile(
            upperQuantile
          ) as user using tracingContext =>
        val polygonBbox: Projected[Polygon] =
          TileUtils.getTileBounds(z, x, y)
        val getEval = tracingContext.span("getEval") use { childContext =>
          Cacheable.getProjectLayerById(layerId, xa, childContext) map {
            layer =>
              layer.geometry match {
                case Some(mask) =>
                  // Intermediate val to anchor the implicit resolution with multiple argument lists
                  val expression: Expression =
                    Masking(
                      List(GeomLit(mask.geom.toGeoJson), RasterVar("mosaic"))
                    )
                  val param =
                    layers.read(
                      layerId,
                      Some(polygonBbox),
                      bandOverride,
                      None,
                      disableColorCorrect getOrElse false,
                      lowerQuantile,
                      upperQuantile,
                      childContext
                    )
                  LayerTms.apply(
                    IO.pure(expression),
                    IO.pure(Map("mosaic" -> param)),
                    ConcurrentInterpreter.DEFAULT[IO],
                    None
                  )
                case _ =>
                  LayerTms.identity(
                    layers.read(
                      layerId,
                      Some(polygonBbox),
                      bandOverride,
                      None,
                      disableColorCorrect getOrElse false,
                      lowerQuantile,
                      upperQuantile,
                      childContext
                    )
                  )
              }
          }
        }

        for {
          fiberAuthProject <-
            authorizers
              .authProject(user, projectId, tracingContext)
              .start
          fiberAuthLayer <-
            authorizers
              .authProjectLayer(projectId, layerId, tracingContext)
              .start
          fiberResp <- getEval.flatMap(_(z, x, y)).start
          _ <-
            (fiberAuthProject, fiberAuthLayer).tupled.join
              .handleErrorWith { error =>
                fiberResp.cancel *> IO.raiseError(error)
              }
          resp <- fiberResp.join flatMap {
            case Valid(tile) =>
              tracingContext.span("render") use { _ =>
                Ok(tile.renderPng.bytes, pngType)
              }
            case Invalid(e) =>
              BadRequest(s"Could not produce tile: $e ")
          }
        } yield resp

      case GET -> Root / UUIDWrapper(projectId) / "layers" / UUIDWrapper(
            layerId
          ) / "histogram" :? BandOverrideQueryParamDecoder(
            overrides
          ) as user using tracingContext =>
        tracingContext.addTags(
          Map(
            "projectId" -> projectId.toString,
            "layerId" -> layerId.toString,
            "requestType" -> "histogram"
          )
        )
        for {
          authFiber <- authorizers.authProject(user, projectId).start
          mosaic = layers.read(
            layerId,
            None,
            overrides,
            None,
            false,
            None,
            None,
            tracingContext
          )
          histFiber <- LayerHistogram.concurrent(mosaic, 4000).start
          _ <- authFiber.join.handleErrorWith { error =>
            histFiber.cancel *> IO.raiseError(error)
          }
          resp <- histFiber.join.flatMap {
            case Valid(hists) =>
              Ok(hists map { hist =>
                Map(hist.binCounts: _*)
              } asJson)
            case Invalid(e) =>
              BadRequest(s"Histograms could not be produced: $e ")
          }
        } yield resp

      case tracedReq @ POST -> Root / UUIDWrapper(
            projectId
          ) / "layers" / UUIDWrapper(
            layerId
          ) / "histogram"
          :? BandOverrideQueryParamDecoder(
            overrides
          ) as user using tracingContext =>
        tracingContext.addTags(
          Map(
            "projectId" -> projectId.toString,
            "layerId" -> layerId.toString,
            "requestType" -> "histogram-layers"
          )
        )
        // Compile to a byte array, decode that as a string, and do something with the results
        tracedReq.authedRequest.req.body.compile.toList flatMap { uuids =>
          decode[List[UUID]](
            uuids map {
              _.toChar
            } mkString
          ) match {
            case Right(uuids) =>
              for {
                authFiber <- authorizers.authProject(user, projectId).start
                mosaic = layers.read(
                  layerId,
                  None,
                  overrides,
                  uuids.toNel,
                  false,
                  None,
                  None
                )
                histFiber <- LayerHistogram.concurrent(mosaic, 4000).start
                _ <- authFiber.join.handleErrorWith { error =>
                  histFiber.cancel *> IO.raiseError(error)
                }
                resp <- histFiber.join.flatMap {
                  case Valid(hists) => Ok(hists asJson)
                  case Invalid(e) =>
                    BadRequest(s"Unable to produce histograms: $e ")
                }
              } yield resp
            case _ =>
              BadRequest(
                """
                    | Could not decode body as sequence of UUIDs.
                    | Format should be, e.g.,
                    | ["342a82e2-a5c1-4b35-bb6b-0b9dd6a52fa7", "8f3bb3fc-4bd6-49e8-9b25-6fa075cc0c77
"]""".trim.stripMargin
              )
          }
        }

      case GET -> Root / UUIDWrapper(projectId) / "analyses" / UUIDWrapper(
            analysisId
          ) / IntVar(z) / IntVar(x) / IntVar(y)
          :? NodeQueryParamMatcher(node) as user using tracingContext =>
        tracingContext.addTags(
          Map(
            "projectId" -> projectId.toString,
            "analysisId" -> analysisId.toString,
            "nodeId" -> node.map(_.toString).getOrElse(""),
            "requestType" -> "analysis-zxy"
          )
        )
        for {
          authFiber <- authorizers.authProject(user, projectId).start
          respFiber <-
            analysisManager
              .tile(user, analysisId, node, z, x, y)
              .start
          _ <- authFiber.join.handleErrorWith { error =>
            respFiber.cancel *> IO.raiseError(error)
          }
          resp <- respFiber.join
        } yield resp

      case GET -> Root / UUIDWrapper(projectId) / "analyses" / UUIDWrapper(
            analysisId
          ) / "histogram" :? NodeQueryParamMatcher(
            node
          ) as user using tracingContext =>
        tracingContext.addTags(
          Map(
            "projectId" -> projectId.toString,
            "analysisId" -> analysisId.toString,
            "nodeId" -> node.map(_.toString).getOrElse(""),
            "requestType" -> "analysis-histogram"
          )
        )
        for {
          authFiber <- authorizers.authProject(user, projectId).start
          respFiber <-
            analysisManager
              .histogram(user, analysisId, node)
              .start
          _ <- authFiber.join.handleErrorWith { error =>
            respFiber.cancel *> IO.raiseError(error)
          }
          resp <- respFiber.join
        } yield resp

      case GET -> Root / UUIDWrapper(projectId) / "analyses" / UUIDWrapper(
            analysisId
          ) / "statistics" :? NodeQueryParamMatcher(
            node
          ) as user using tracingContext =>
        tracingContext.addTags(
          Map(
            "projectId" -> projectId.toString,
            "analysisId" -> analysisId.toString,
            "nodeId" -> node.map(_.toString).getOrElse(""),
            "requestType" -> "analysis-stats"
          )
        )
        for {
          authFiber <- authorizers.authProject(user, projectId).start
          respFiber <-
            analysisManager
              .statistics(user, analysisId, node)
              .start
          _ <- authFiber.join.handleErrorWith { error =>
            respFiber.cancel *> IO.raiseError(error)
          }
          resp <- respFiber.join
        } yield resp

      case tracedReq @ GET -> Root / UUIDWrapper(
            projectId
          ) / "analyses" / UUIDWrapper(
            analysisId
          ) / "raw"
          :? ExtentQueryParamMatcher(extent)
          :? ZoomQueryParamMatcher(zoom)
          :? NodeQueryParamMatcher(node) as user using tracingContext =>
        tracingContext.addTags(
          Map(
            "projectId" -> projectId.toString,
            "analysisId" -> analysisId.toString,
            "zoom" -> zoom.toString,
            "nodeId" -> node.map(_.toString).getOrElse(""),
            "requestType" -> "analysis-export"
          )
        )
        for {
          authFiber <-
            authorizers
              .authProjectAnalysis(user, projectId, analysisId)
              .start
          respFiber <-
            analysisManager
              .export(
                tracedReq.authedRequest,
                user,
                analysisId,
                node,
                extent,
                zoom
              )
              .start
          _ <- authFiber.join.handleErrorWith { error =>
            respFiber.cancel *> IO.raiseError(error)
          }
          resp <- respFiber.join
        } yield resp
    }
}
