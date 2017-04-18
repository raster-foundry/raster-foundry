package com.azavea.rf.tile.routes

import com.azavea.rf.common.Authentication
import com.azavea.rf.tile.image._
import com.azavea.rf.tile.tool.TileSources
import com.azavea.rf.tile._
import com.azavea.rf.tool.ast._
import com.azavea.rf.database.Database
import com.azavea.rf.database.tables.Tools
import com.azavea.rf.datamodel._
import com.azavea.rf.tool.ast.codec._
import com.azavea.rf.tool.ast._
import com.azavea.rf.tool.op._

import io.circe._
import io.circe.parser._
import io.circe.syntax._
import io.circe.generic.auto._
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import geotrellis.raster._
import geotrellis.raster.render._
import geotrellis.raster.io._
import geotrellis.raster.op._
import geotrellis.raster.render.{Png, ColorRamp, ColorMap}
import geotrellis.raster.io.geotiff._
import geotrellis.vector.Extent
import geotrellis.slick.Projected
import geotrellis.spark._
import geotrellis.spark.tiling._
import geotrellis.proj4._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{ContentType, HttpEntity, HttpResponse, MediaTypes}
import com.typesafe.scalalogging.LazyLogging
import cats.data._
import cats.data.Validated._
import cats.implicits._

import java.security.InvalidParameterException
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import java.util.UUID
import java.io._


class ToolRoutes(implicit val database: Database) extends Authentication with LazyLogging {
  val userId: String = "rf_airflow-user"

  val defaultRamps = Map(
    "viridis" -> geotrellis.raster.render.ColorRamps.Viridis,
    "inferno" -> geotrellis.raster.render.ColorRamps.Inferno,
    "magma" -> geotrellis.raster.render.ColorRamps.Magma,
    "lightYellowToOrange" -> geotrellis.raster.render.ColorRamps.LightYellowToOrange,
    "classificationBoldLandUse" -> geotrellis.raster.render.ColorRamps.ClassificationBoldLandUse
  )


  def pngAsHttpResponse(png: Png): HttpResponse =
    HttpResponse(entity = HttpEntity(ContentType(MediaTypes.`image/png`), png.bytes))

  def parseBreakMap(str: String): Map[Double,Double] = {
    str.split(';').map { c: String =>
      val Array(a, b) = c.trim.split(':').map(_.toDouble)
      (a, b)
    }.toMap
  }

  def root(
    source: (RFMLRaster, Int, Int, Int) => Future[Option[Tile]]
  ): Route =
    pathPrefix(JavaUUID){ (toolId) =>
      // TODO: check token for organization access
      val futureTool: Future[Tool.WithRelated] = Tools.getTool(toolId).map {
        _.getOrElse(throw new java.io.IOException(toolId.toString))
      }

      (pathEndOrSingleSlash & get & rejectEmptyResponse) {
        complete(futureTool)
      } ~
      pathPrefix(IntNumber / IntNumber / IntNumber){ (z, x, y) =>
        parameter(
          'node.?,
          'geotiff.?(false),
          'ramp.?("viridis")
        )
        { (node, geotiffOutput, colorRamp) =>
          complete {
            val nodeId = node.map(UUID.fromString(_))
            for {
              tool <- futureTool
              maybeHist <- LayerCache.modelLayerGlobalHistogram(toolId, nodeId).value
            } yield {
              logger.debug(s"Tool JSON: $tool")

              // Parse json to AST and select subnode if specified
              // TODO: return useful HTTP errors on parse failure
              val ast = tool.definition.as[MapAlgebraAST] match {
                case Right(ast) =>
                  nodeId match {
                    case Some(id) =>
                      ast.find(id).getOrElse(throw new InvalidParameterException(s"AST has no node with the id $id"))
                    case None =>
                      ast
                  }
                case Left(failure) =>
                  throw failure
              }

              val tms = Interpreter.interpretTMS(ast, source)

              for {
                op <- tms(z, x, y)
              } yield {
                op match {
                  case Valid(op) =>
                    /** TODO: We should spend some time thinking about NODATA on pain of our
                      *        mishandling the translucent portion of tiles.
                      */
                    val tile = op.toTile(DoubleCellType).get

                    (op.toTile(DoubleCellType), defaultRamps.get(colorRamp), maybeHist) match {
                      case (Some(tile), Some(cmap), Some(hist)) =>
                        val png = tile.renderPng(cmap.toColorMap(hist))
                        Future.successful { pngAsHttpResponse(png) }
                      case (None, _, _) =>
                        Marshal(500 -> List("Unable to calculate tile from this operation")).to[HttpResponse]
                      case (_, None, _) =>
                        Marshal(400 -> List(s"Unknown colormap: ${colorRamp}")).to[HttpResponse]
                      case (_, _, None) =>
                        Marshal(400 -> List("Unable to find global histogram for layer")).to[HttpResponse]
                    }
                  case Invalid(errors) =>
                    Marshal(400 -> errors.toList).to[HttpResponse]
              }
            }
          }
        }
      }
    }
  }
}

