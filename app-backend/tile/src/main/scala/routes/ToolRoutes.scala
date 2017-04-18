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


class ToolRoutes(implicit val database: Database) extends Authentication with LazyLogging {
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
    pathPrefix(JavaUUID){ (toolId) =>
      authenticate { user =>
        // TODO: check token for organization access
        val futureTool: Future[Option[Tool.WithRelated]] = Tools.getTool(toolId, user)

        (pathEndOrSingleSlash & get & rejectEmptyResponse) {
          complete(futureTool)
        } ~
        pathPrefix(IntNumber / IntNumber / IntNumber) { (z, x, y) =>
          parameter(
            'node.?,
            'geotiff.?(false),
            'cramp.?("viridis")
          )
          { (node, geotiffOutput, colorRamp) =>
            complete {
              val nodeId = node.map(UUID.fromString(_))
              val responsePng = for {
                tool <- OptionT(futureTool)
                ramp <- OptionT.fromOption[Future](defaultRamps.get(colorRamp))
                hist <- LayerCache.modelLayerGlobalHistogram(tool, nodeId)
                ast  <- OptionT.fromOption[Future](tool.definition.as[MapAlgebraAST] match {
                          case Right(entireAST) =>
                            nodeId match {
                              case Some(id) => entireAST.find(id)
                              case None => Some(entireAST)
                            }
                          case Left(failure) =>
                            throw failure
                        })
                tile <- OptionT({
                          val tms = Interpreter.interpretTMS(ast, source)
                          for {
                            operation <- tms(z, x, y)
                          } yield {
                            operation match {
                              case Valid(op) => op.toTile(DoubleCellType)
                              case Invalid(errors) => throw InterpreterException(errors)
                            }
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

