package com.rasterfoundry.tile.routes

import com.rasterfoundry.akkautil.{CommonHandlers, Authentication}
import com.rasterfoundry.common._
import com.rasterfoundry.common.ast._
import com.rasterfoundry.datamodel.User
import com.rasterfoundry.tile._
import com.rasterfoundry.tile.image._
import com.rasterfoundry.tile.tool._
import com.rasterfoundry.tool.ast._
import com.rasterfoundry.tool.eval._
import com.rasterfoundry.tool.maml._
import com.rasterfoundry.database.util.RFTransactor

import com.azavea.maml.ast._
import com.azavea.maml.error._
import com.azavea.maml.eval._
import com.azavea.maml.eval.directive._
import com.azavea.maml.util._
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import geotrellis.proj4._
import geotrellis.raster._
import geotrellis.raster.render._
import geotrellis.raster.render.png._
import geotrellis.raster.io.geotiff.SinglebandGeoTiff
import geotrellis.vector.{Extent, Projected}
import akka.http.scaladsl.marshalling._
import akka.http.scaladsl.model.{
  ContentType,
  HttpEntity,
  MediaTypes,
  StatusCodes,
  HttpResponse
}
import akka.http.scaladsl.server._
import cats.data.Validated._
import cats.data.{NonEmptyList => NEL, _}
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import cats.effect.IO

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import java.util.UUID

import com.rasterfoundry.common.cache.CacheClient
import com.rasterfoundry.common.cache.kryo.KryoMemcachedClient

class ToolRoutes
    extends Authentication
    with LazyLogging
    with CommonHandlers
    with KamonTraceDirectives {

  implicit lazy val xa = RFTransactor.xa

  lazy val memcachedClient = KryoMemcachedClient.default
  val rfCache = new CacheClient(memcachedClient)

  val providedRamps = Map(
    "viridis" -> geotrellis.raster.render.ColorRamps.Viridis,
    "inferno" -> geotrellis.raster.render.ColorRamps.Inferno,
    "magma" -> geotrellis.raster.render.ColorRamps.Magma,
    "lightYellowToOrange" -> geotrellis.raster.render.ColorRamps.LightYellowToOrange,
    "classificationBoldLandUse" -> geotrellis.raster.render.ColorRamps.ClassificationBoldLandUse
  )

  implicit val pngMarshaller: ToEntityMarshaller[Png] = {
    val contentType = ContentType(MediaTypes.`image/png`)
    Marshaller.withFixedContentType(contentType) { png ⇒
      HttpEntity(contentType, png.bytes)
    }
  }

  def parseBreakMap(str: String): Map[Double, Double] = {
    str
      .split(';')
      .map { c: String =>
        val Array(a, b) = c.trim.split(':').map(_.toDouble)
        (a, b)
      }
      .toMap
  }

  /** Endpoint to be used for kicking the histogram cache and ensuring tiles are quickly loaded */
  def preflight(toolRunId: UUID, user: User) = {
    traceName("toolrun-preflight") {
      parameter(
        'node.?,
        'voidCache.as[Boolean].?(false)
      ) { (node, void) =>
        val nodeId = node.map(UUID.fromString(_))
        onSuccess(
          LayerCache
            .toolEvalRequirements(toolRunId, nodeId, user, void)
            .value) { _ =>
          complete {
            StatusCodes.NoContent
          }
        }
      }
    }
  }

  /** Endpoint used to verify that a [[com.rasterfoundry.datamodel.ToolRun]] is sufficient to
    *  evaluate the [[com.rasterfoundry.datamodel.Tool]] to which it refers
    */
  def validate(toolRunId: UUID, user: User) = {
    traceName("toolrun-validate") {
      pathPrefix("validate") {
        complete {
          for {
            (_, ast) <- LayerCache.toolEvalRequirements(toolRunId, None, user)
          } yield validateTreeWithSources[Unit](ast)
          StatusCodes.NoContent
        }
      }
    }
  }

  /** Endpoint used to get a [[com.rasterfoundry.datamodel.ToolRun]] histogram */
  def histogram(toolRunId: UUID, user: User) = {
    traceName("toolrun-histogram") {
      pathPrefix("histogram") {
        parameter(
          'node.?,
          'voidCache.as[Boolean].?(false)
        ) { (node, void) =>
          complete {
            val nodeId = node.map(UUID.fromString(_))
            LayerCache
              .modelLayerGlobalHistogram(toolRunId, nodeId, user, void)
              .value
          }
        }
      }
    }
  }

  /** Endpoint used to get a [[ToolRun]] statistics */
  def statistics(toolRunId: UUID, user: User) = {
    traceName("toolrun-statistics") {
      pathPrefix("statistics") {
        parameter(
          'node.?,
          'voidCache.as[Boolean].?(false)
        ) { (node, void) =>
          complete {
            val nodeId = node.map(UUID.fromString(_))
            LayerCache
              .modelLayerGlobalHistogram(toolRunId, nodeId, user, void)
              .mapFilter(_.statistics)
              .value
          }
        }
      }
    }
  }

  val tileResolver =
    new TileResolver(implicitly[Transactor[IO]], implicitly[ExecutionContext])
  val tmsInterpreter = BufferingInterpreter.DEFAULT
  val emptyPng =
    IntConstantNoDataArrayTile(Array(0), 1, 1).renderPng(RgbaPngEncoding)
  val emptyTile = IntConstantNoDataArrayTile(Array(0), 1, 1)

  /** The central endpoint for ModelLab; serves TMS tiles given a [[ToolRun]] specification */
  def tms(
      toolRunId: UUID,
      user: User
  ): Route =
    handleExceptions(circeDecodingError) {
      traceName("toolrun-tms") {
        pathPrefix(IntNumber / IntNumber / IntNumber) { (z, x, y) =>
          parameter(
            'node.?,
            'cramp.?("viridis")
          ) { (node, colorRampName) =>
            val nodeId = node.map(UUID.fromString(_))
            val colorRamp =
              providedRamps.getOrElse(colorRampName, providedRamps("viridis"))
            val components = for {
              (lastUpdateTime, ast) <- LayerCache.toolEvalRequirements(
                toolRunId,
                nodeId,
                user)
              (updatedAst) <- OptionT(RelabelAst.cogScenes(ast))
              (expression, metadata) <- OptionT.pure[Future](updatedAst.asMaml)
              cMap <- LayerCache.toolRunColorMap(toolRunId,
                                                 nodeId,
                                                 user,
                                                 colorRamp,
                                                 colorRampName)
            } yield (expression, metadata, cMap, lastUpdateTime)

            complete {
              components.value.flatMap({ data =>
                val result: Future[Option[Png]] = data match {
                  case Some((expression, metadata, cMap, updateTime)) =>
                    val cacheKey = s"toolrun-tms-${toolRunId}-${nodeId
                      .getOrElse("")}-$z-$x-$y-${updateTime.getTime}"
                    rfCache
                      .cachingOptionT(cacheKey)({
                        val literalTree =
                          tileResolver.resolveBuffered(expression)(z, x, y)
                        val interpretedTile: Future[Interpreted[Tile]] =
                          literalTree.map({ resolvedAst =>
                            resolvedAst
                              .andThen({ tmsInterpreter(_) })
                              .andThen({ _.as[Tile] })
                          })
                        OptionT({
                          interpretedTile.map({
                            case Valid(tile) =>
                              logger.debug(
                                s"Tile successfully produced at $z/$x/$y")
                              metadata
                                .flatMap({ md =>
                                  md.renderDef.map({ renderDef =>
                                    maybeResample(tile) renderPng (renderDef)
                                  })
                                })
                                .orElse({
                                  Some(maybeResample(tile).renderPng(cMap))
                                })
                            case Invalid(nel) =>
                              throw new Exception(
                                s"Tile could not be resolved: $nel")
                          })
                        })
                      })
                      .value
                  case _ => Future.successful(None)
                }
                result
              })
            }
          }
        }
      }
    }

  def raw(
      toolRunId: UUID,
      user: User
  ): Route =
    handleExceptions(circeDecodingError) {
      traceName("analysis-raw") {
        pathPrefix("raw") {
          parameter("bbox", "zoom".as[Int], "node".?) { (bbox, zoom, node) =>
            val nodeId = node.map(UUID.fromString(_))
            val components = for {
              (lastUpdateTime, ast) <- LayerCache.toolEvalRequirements(
                toolRunId,
                nodeId,
                user)
              (expression, metadata) <- OptionT.pure[Future](ast.asMaml)
            } yield (expression, metadata, lastUpdateTime)
            complete {
              components.value.flatMap({ data =>
                val result: Future[Option[HttpResponse]] = data match {
                  case Some((expression, metadata, updateTime)) =>
                    val extent = Projected(
                      Extent.fromString(bbox).toPolygon,
                      4326
                    ).reproject(LatLng, WebMercator)(3857).envelope
                    val literalTree =
                      tileResolver.resolveForExtent(expression, zoom, extent)
                    val interpretedTile: Future[Interpreted[Tile]] =
                      literalTree.map({ resolvedAst =>
                        resolvedAst
                          .andThen({ tmsInterpreter(_) })
                          .andThen({ _.as[Tile] })
                      })
                    interpretedTile.map({
                      case Valid(tile: Tile) =>
                        logger.debug(
                          s"Tile successfully produced at $zoom, $extent")
                        val tiff = SinglebandGeoTiff(tile, extent, WebMercator)
                        Some(
                          HttpResponse(entity =
                            HttpEntity(ContentType(MediaTypes.`image/tiff`),
                                       tiff.toByteArray)))
                      case Invalid(nel) =>
                        throw new Exception(s"Tile could not be resolved: $nel")
                    })
                  case _ => Future.successful(None)
                }
                result
              })
            }
          }
        }
      }
    }

  @inline def maybeResample(tile: Tile): Tile =
    if (tile.dimensions != (256, 256)) tile.resample(256, 256) else tile
}
