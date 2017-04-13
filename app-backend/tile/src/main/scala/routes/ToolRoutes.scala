package com.azavea.rf.tile.routes

import com.azavea.rf.tile.image._
import com.azavea.rf.database.Database

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
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import com.typesafe.scalalogging.LazyLogging
import spray.json._
import cats.implicits._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import java.util.UUID

// TODO: I need a better way to bind ops ... Right now I just swap out Var for Op ...

object ToolRoutes extends LazyLogging {
  val userId: String = "rf_airflow-user"

  val red = Op('red)
  val nir = Op('nir)
  val ndvi = (nir - red) / (nir + red)

  def ndvi0 = {
    val params0 = Map(
      Op.Var('red) -> Op("LC8_0[3]"),
      Op.Var('nir) -> Op("LC8_0[4]"))
    ndvi.bind(params0)
  }

  def ndvi1 = {
    val params1 = Map(
      Op.Var('red) -> Op("LC8_1[3]"),
      Op.Var('nir) -> Op("LC8_1[4]"))
    ndvi.bind(params1)
  }

  def reclass0(breaks: Map[Double, Double]) = {
    Reclassify(ndvi0, BreakMap(breaks))
  }

  def reclass1(breaks: Map[Double, Double]) = {
    Reclassify(ndvi1, BreakMap(breaks))
  }

  def ndviDiff(breaks0: Map[Double, Double], breaks1: Map[Double, Double]) = {
    reclass1(breaks1) - reclass0(breaks0)
  }

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
        complete("model JSON")
      } ~
      pathPrefix(IntNumber / IntNumber / IntNumber){ (z, x, y) =>
        parameter(
          'part.?,
          'LC8_0, 'LC8_1,
          'class0.?("0.1:0;99999999:1.0"), // Make this big so that all values are caught
          'class1.?("0.1:0;99999999:1.0"),
          'geotiff.?(false),
          'cm.?
        )
        { (partId, p0, p1, class0, class1, geotiffOutput, colorMap) =>
          complete {
            val varMap = Map(
              'LC8_0 -> { (z: Int, x: Int, y: Int) =>
                Mosaic.raw(UUID.fromString(p0), z, x, y).value},
              'LC8_1 -> { (z: Int, x: Int, y: Int) =>
                Mosaic.raw(UUID.fromString(p1), z, x, y).value})

            val model: Op = partId match {
              case Some("ndvi0") => ndvi0
              case Some("ndvi1") => ndvi1
              case Some("class0") => reclass0(parseBreakMap(class0))
              case Some("class1") => reclass1(parseBreakMap(class1))
              case _ => ndviDiff(parseBreakMap(class0), parseBreakMap(class1))
            }

            logger.debug(s"model prior to param binding: $model")

            // TODO: Inspect what variables are actually required, only fetch those tiles
            val futureTiles = varMap.map { case (sym, tms) =>
              tms(z, x, y).map { maybeTile => sym -> maybeTile }
            }

            Future.sequence(futureTiles).map { tiles =>
              // construct a list of parameters to bind to the NDVI-diff model
              val params = tiles.map { case (name, maybeTile) =>
                Op.Var(name) -> Op.Unbound(maybeTile)
              }.toMap

              logger.debug(s"Params to bind to model: $params")
              val assignedModel = model.bind(params)
              logger.debug(s"model after param binding: $assignedModel")
              assert(assignedModel.fullyBound) // Verification that binding completed

              val calculation = assignedModel.toTile(FloatCellType)

              calculation.map { tile =>
                if (geotiffOutput) {
                  // Largely for debugging, the Extent and CRS are *NOT* meaningful
                  val geotiff = SinglebandGeoTiff(tile, Extent(0, 0, 0, 0), CRS.fromEpsgCode(3857))
                  HttpResponse(entity = HttpEntity(ContentType(MediaTypes.`image/tiff`), geotiff.toByteArray))
                } else {
                  val png = tile.renderPng(lookupColorMap(colorMap))
                  pngAsHttpResponse(png)
                }
              }
            }
          }
        }
      }
    }
}
