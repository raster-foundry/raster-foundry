package com.azavea.rf.tile.routes

import java.util.UUID

import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global

import akka.http.scaladsl.marshalling._
import akka.http.scaladsl.model.{ContentType, HttpEntity, MediaTypes}
import akka.http.scaladsl.server._
import cats.data._
import cats.data.Validated._
import cats.implicits._
import com.azavea.rf.common._
import com.azavea.rf.database.Database
import com.azavea.rf.database.tables.{ToolRuns, Tools}
import com.azavea.rf.tile._
import com.azavea.rf.tool.ast._
import com.azavea.rf.tool.eval._
import com.azavea.rf.tool.params._
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import geotrellis.raster._
import geotrellis.raster.render._


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
                  params  <- OptionT.fromOption[Future](maybeThrow(toolRun.executionParameters.as[EvalParams])(identity))
                  ramp    <- OptionT.fromOption[Future](defaultRamps.get(colorRamp))
                  ast     <- OptionT.fromOption[Future](maybeThrow(tool.definition.as[MapAlgebraAST])(entireAST =>
                    nodeId.flatMap(id => entireAST.find(id)).orElse(Some(entireAST))).flatten
                  )
                  hist    <- LayerCache.modelLayerGlobalHistogram(toolRun, tool, nodeId)
                  tile    <- OptionT({
                               val tms = Interpreter.interpretTMS(ast, params, source)
                               tms(z, x, y).map {
                                 case Valid(op) => op.evaluateDouble
                                 case Invalid(errors) => throw InterpreterException(errors)
                               }
                             })
                } yield tile.renderPng(ramp.toColorMap(hist))
                responsePng.value
              }
            }
          }
        } ~
        pathPrefix("validate") {
          handleExceptions(interpreterExceptionHandler) {
            complete(validateAST[Unit](toolRunId, user))
          }
        }
      }
    }
}
