package com.azavea.rf.tile.tools

import java.util.UUID

import com.azavea.rf.common._
import com.azavea.rf.common.ast._
import com.azavea.rf.database.Database
import com.azavea.rf.datamodel.{Tool, ToolRun}
import com.azavea.rf.tile._
import com.azavea.rf.tool.ast._
import com.azavea.rf.tool.eval._
import com.azavea.rf.tool.params._

import akka.dispatch.MessageDispatcher
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server._
import cats.data.Validated._
import cats.data._
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import geotrellis.raster._

import scala.concurrent._

trait ToolRoutes extends Authentication
    with LazyLogging
    with InterpreterExceptionHandling
    with CommonHandlers
    with ToolTiles
    with TileAuthentication
    with KamonTraceRF {

  val userId: String = "rf_airflow-user"
  implicit val database: Database
  implicit val blockingDispatcher: MessageDispatcher

  val exceptionHandlingDirective = handleExceptions(interpreterExceptionHandler) & handleExceptions(circeDecodingError)
  val toolRunIdDirective = pathPrefix(JavaUUID)
  val combinedToolDirectives = exceptionHandlingDirective & toolRunIdDirective & authenticateWithParameter
  val toolRoutes: Route = {
    get {
      tileAuthenticateOption { _ =>
        tms(getTileWithNeighbors) ~
          validate ~
          histogram ~
          preflight
      }
    }
  }

  val defaultRamps = Map(
    "viridis" -> geotrellis.raster.render.ColorRamps.Viridis,
    "inferno" -> geotrellis.raster.render.ColorRamps.Inferno,
    "magma" -> geotrellis.raster.render.ColorRamps.Magma,
    "lightYellowToOrange" -> geotrellis.raster.render.ColorRamps.LightYellowToOrange,
    "classificationBoldLandUse" -> geotrellis.raster.render.ColorRamps.ClassificationBoldLandUse
  )

  def parseBreakMap(str: String): Map[Double,Double] = {
    str.split(';').map { c: String =>
      val Array(a, b) = c.trim.split(':').map(_.toDouble)
      (a, b)
    }.toMap
  }

  /** Endpoint to be used for kicking the histogram cache and ensuring tiles are quickly loaded */
  val preflight = exceptionHandlingDirective {
    combinedToolDirectives { (toolRunId, user) =>
      traceName("toolrun-preflight") {
        parameter(
          'node.?,
          'voidCache.as[Boolean].?(false)
        ) { (node, void) =>
          val nodeId = node.map(UUID.fromString(_))
          onSuccess(toolEvalRequirements(toolRunId, nodeId, user, void).value) { _ =>
            complete {
              StatusCodes.NoContent
            }
          }
        }
      }
    }
  }

  /** Endpoint used to verify that a [[ToolRun]] is sufficient to
    *  evaluate the [[Tool]] to which it refers
    */
  val validate =
    combinedToolDirectives { (toolRunId, user) =>
      traceName("toolrun-validate") {
        pathPrefix("validate") {
          complete(validateAST[Unit](toolRunId, user))
        }
      }
    }

  /** Endpoint used to get a [[ToolRun]] histogram */
  val histogram =
    combinedToolDirectives { (toolRunId, user) =>
      traceName("toolrun-histogram") {
        pathPrefix("histogram") {
          parameter(
            'node.?,
            'voidCache.as[Boolean].?(false)
          ) { (node, void) =>
            complete {
              val nodeId = node.map(UUID.fromString(_))
              modelLayerGlobalHistogram(toolRunId, nodeId, user, void).value
            }
          }
        }
      }
    }

  /** The central endpoint for ModelLab; serves TMS tiles given a [[ToolRun]] specification */
  def tms(
    source: (RFMLRaster, Boolean, Int, Int, Int) => Future[Option[TileWithNeighbors]]
  ): Route =
    combinedToolDirectives { (toolRunId, user) =>
      traceName("toolrun-tms") {
        pathPrefix(IntNumber / IntNumber / IntNumber) { (z, x, y) =>
          parameter(
            'node.?,
            'geotiff.?(false),
            'cramp.?("viridis")
          ) { (node, geotiffOutput, colorRamp) =>
            complete {
              val nodeId = node.map(UUID.fromString(_))
              val responsePng = for {
                (toolRun, tool) <- toolAndToolRun(toolRunId, user)
                (ast, params) <- toolEvalRequirements(toolRunId, nodeId, user)
                tile <- OptionT({
                  val tms = Interpreter.interpretTMS(ast, params.sources, params.overrides, source, 256)
                  logger.debug(s"Attempting to retrieve TMS tile at $z/$x/$y")
                  tms(z, x, y).map {
                    case Valid(op) => op.evaluateDouble
                    case Invalid(errors) => throw InterpreterException(errors)
                  }
                })
                cMap <- toolRunColorMap(toolRunId, nodeId, user)
              } yield {
                logger.debug(s"Tile successfully produced at $z/$x/$y")
                tile.renderPng(cMap)
              }
              responsePng.value
            }
          }
        }
      }
    }
}
