package com.azavea.rf.tile.routes

import com.azavea.rf.common.Authentication
import com.azavea.rf.tile.image._
import com.azavea.rf.tile.tool.TileSources
import com.azavea.rf.tile.directives._
import com.azavea.rf.tile._
import com.azavea.rf.database.Database
import com.azavea.rf.database.tables.{Tools, ToolRuns}
import com.azavea.rf.datamodel._
import com.azavea.rf.tool.ast.codec._
import com.azavea.rf.tool.ast._
import com.azavea.rf.tool.eval._
import com.azavea.rf.tool.params._

import io.circe._
import io.circe.parser._
import io.circe.syntax._
import io.circe.generic.auto._
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import geotrellis.raster._
import geotrellis.raster.render._
import geotrellis.raster.io._
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
import akka.http.scaladsl.marshalling.PredefinedToEntityMarshallers._
import akka.http.scaladsl.marshalling._
import com.typesafe.scalalogging.LazyLogging
import cats.data._
import cats.data.Validated._
import cats.implicits._

import java.security.InvalidParameterException
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import java.util.UUID
import java.io._


class ToolRoutes(implicit val database: Database) extends Authentication
  with LazyLogging
  with InterpreterErrorHandler {
  val userId: String = "rf_airflow-user"

  val defaultRamps = Map(
    "viridis" -> geotrellis.raster.render.ColorRamps.Viridis,
    "inferno" -> geotrellis.raster.render.ColorRamps.Inferno,
    "magma" -> geotrellis.raster.render.ColorRamps.Magma,
    "lightYellowToOrange" -> geotrellis.raster.render.ColorRamps.LightYellowToOrange,
    "classificationBoldLandUse" -> geotrellis.raster.render.ColorRamps.ClassificationBoldLandUse
  )

  implicit val pngMarshaller: ToEntityMarshaller[Png] = {
    val contentType = ContentType(MediaTypes.`image/png`)
    Marshaller.withFixedContentType(contentType) { png ⇒ HttpEntity(contentType, png.bytes) }
  }

  def parseBreakMap(str: String): Map[Double,Double] = {
    str.split(';').map { c: String =>
      val Array(a, b) = c.trim.split(':').map(_.toDouble)
      (a, b)
    }.toMap
  }

  def root(
    source: (RFMLRaster, Int, Int, Int) => Future[Option[Tile]]
  ): Route =
    pathPrefix(JavaUUID){ (toolRunId) =>
      authenticate { user =>
        // TODO: check token for organization access
        (pathEndOrSingleSlash & get & rejectEmptyResponse) {
          complete {
            (for {
              toolRun <- OptionT(database.db.run(ToolRuns.getToolRun(toolRunId, user)))
              tool    <- OptionT(Tools.getTool(toolRun.tool, user))
            } yield tool).value
          }
        } ~
        pathPrefix(IntNumber / IntNumber / IntNumber) { (z, x, y) =>
          parameter(
            'node.?,
            'geotiff.?(false),
            'cramp.?("viridis")
          ) { (node, geotiffOutput, colorRamp) =>
            handleExceptions(interpreterExceptionHandler) {
              complete {
                val nodeId = node.map(UUID.fromString(_))
                val responsePng = for {
                  toolRun <- OptionT(database.db.run(ToolRuns.getToolRun(toolRunId, user)))
                  tool    <- OptionT(Tools.getTool(toolRun.tool, user))
                  params  <- OptionT.fromOption[Future](toolRun.executionParameters.as[EvalParams] match {
                               case Right(toolRunParams) => Some(toolRunParams)
                               case Left(failure) => throw failure
                             })
                  ramp    <- OptionT.fromOption[Future](defaultRamps.get(colorRamp))
                  ast     <- OptionT.fromOption[Future](tool.definition.as[MapAlgebraAST] match {
                               case Right(entireAST) =>
                                 nodeId.flatMap(id => entireAST.find(id)).orElse(Some(entireAST))
                               case Left(failure) =>
                                 throw failure
                             })
                  hist    <- LayerCache.modelLayerGlobalHistogram(toolRun, tool, nodeId)
                  tile    <- OptionT({
                               val tms = Interpreter.interpretTMS(ast, params, source)
                               tms(z, x, y).map {
                                 case Valid(op) =>
                                   op.evaluateDouble
                                 case Invalid(errors) =>
                                   throw InterpreterException(errors)
                               }
                             })
                } yield tile.renderPng(ramp.toColorMap(hist))
                responsePng.value
              }
            }
          }
        }
      }
    }
}

