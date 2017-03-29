package com.azavea.rf.tile.routes

import com.azavea.rf.tile.image._
import com.azavea.rf.tile._
import com.azavea.rf.tool.ast._
import com.azavea.rf.database.Database
import com.azavea.rf.database.tables.Tools
import com.azavea.rf.datamodel._
import com.azavea.rf.tool.ast.codec._
import com.azavea.rf.tool.ast._

import io.circe._
import io.circe.parser._
import io.circe.syntax._
import io.circe.generic.auto._
import geotrellis.raster._
import geotrellis.raster.render._
import geotrellis.raster.io._
import geotrellis.raster.op._
import geotrellis.raster.render.{Png, ColorRamp, ColorMap}
import geotrellis.raster.io.geotiff._
import geotrellis.vector.Extent
import geotrellis.spark._
import geotrellis.proj4.CRS
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.{ContentType, HttpEntity, HttpResponse, MediaTypes}
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import spray.json._
//import cats.implicits._
//import org.scalatest._
//import cats.syntax.either._
import cats.data._
import cats.implicits._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import java.util.UUID


class ToolRoutes(implicit db: Database) extends LazyLogging {
  val userId: String = "rf_airflow-user"

  def lookupColorMap(str: Option[String]): ColorMap = {
    str match {
      case Some(s) if s.contains(':') =>
        ColorMap.fromStringDouble(s).get
      case None =>
        val colorRamp = ColorRamp(Vector(0xD51D26FF, 0xDD5249FF, 0xE6876CFF, 0xEFBC8FFF, 0xF8F2B2FF, 0xC7DD98FF, 0x96C87EFF, 0x65B364FF, 0x349E4BFF))
        val breaks = Array[Double](0.05, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 1.0)
        ColorMap(breaks, colorRamp)
      case Some(_) =>
        throw new Exception("color map string is all messed up")
    }
  }

  def pngAsHttpResponse(png: Png): HttpResponse =
    HttpResponse(entity = HttpEntity(ContentType(MediaTypes.`image/png`), png.bytes))

  def parseBreakMap(str: String): Map[Double,Double] = {
    str.split(';').map { c: String =>
      val Array(a, b) = c.trim.split(':').map(_.toDouble)
      (a, b)
    }.toMap
  }

  def root(implicit database: Database): Route =
    pathPrefix(Segment / "ndvi-diff-tool"){ organizationId =>
      (pathEndOrSingleSlash & get & rejectEmptyResponse) {
        complete(futureTool)
      } ~
      pathPrefix(IntNumber / IntNumber / IntNumber){ (z, x, y) =>
        parameter(
          'part.?,
          'geotiff.?(false),
          'cm.?
        )
        { (partId, geotiffOutput, colorMap) =>
          complete {
            OptionT(futureTool).mapFilter { tool =>
              logger.debug(s"Raw Tool: $tool")
              // TODO: return useful HTTP errors on parse failure
              tool.definition.as[MapAlgebraAST] match {
                case Left(failure) =>
                  logger.error(s"Failed to parse MapAlgebraAST from: ${tool.definition.noSpaces} with $failure")
                  None

                case Right(ast) =>
                  logger.debug(s"Parsed Tool: ${ast}")
                  ast.some
              }
            }.map { ast =>
              // TODO: can we move it outside the z/x/y to get some re-use? (don't think so but should check)
              val tms = Interpreter.tms(ast, source)
              OptionT(tms(z,x,y)).map { op =>
                val tile = op.toTile(IntCellType).get
                // TODO: use color ramp to paint the tile

                if (geotiffOutput) {
                  // Largely for debugging, the Extent and CRS are *NOT* meaningful
                  val geotiff = SinglebandGeoTiff(tile, Extent(0, 0, 0, 0), CRS.fromEpsgCode(3857))
                  HttpResponse(entity = HttpEntity(ContentType(MediaTypes.`image/tiff`), geotiff.toByteArray))
                } else {
                  val png = tile.renderPng(lookupColorMap(colorMap))
                  pngAsHttpResponse(png)
                }
              }.value
            }.value
          }
        }
      }
    }
}
