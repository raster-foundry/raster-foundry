package com.azavea.rf.tile.routes

import com.azavea.rf.tile.image._
import com.azavea.rf.database.Database

import geotrellis.raster._
import geotrellis.raster.render._
import geotrellis.raster.io._
import geotrellis.raster.op._
import geotrellis.raster.render.{Png, ColorRamp, ColorMap}
import geotrellis.spark._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.{ContentType, HttpEntity, HttpResponse, MediaTypes}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import com.typesafe.scalalogging.LazyLogging
import spray.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import java.util.UUID

// TODO: I need a better way to bind shit ... Right now I just swap out Var for Op ...

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
      case Some(s) if str.contains(':') =>
        ColorMap.fromString(s).get
      case None =>
        val colorRamp = ColorRamp(Array[Int](0xffffe5aa, 0xf7fcb9ff, 0xd9f0a3ff, 0xaddd8eff, 0x78c679ff, 0x41ab5dff, 0x238443ff, 0x006837ff, 0x004529ff))
        val breaks = Array[Double](0.05, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 1.0)
        ColorMap(breaks, colorRamp)
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

  def root(implicit db: Database) =
    pathPrefix(Segment / "ndvi-diff-tool"){ organizationId =>
      (pathEndOrSingleSlash & get & rejectEmptyResponse) {
        complete("model JSON")
      } ~
      pathPrefix(IntNumber / IntNumber / IntNumber){ (z, x, y) =>
        parameter(
          'part.?,
          'LC8_0, 'LC8_1,
          'class0.?("-1:0;0.01:1.0"),
          'class1.?("-1:0;0.01:1.0"),
          'cm.?
        )
        { (partId, p0, p1, class0, class1, colorMap) =>
          complete {
            val orgId = UUID.fromString(organizationId)
            val varMap = Map(
              'LC8_0 -> { (z: Int, x: Int, y: Int) =>
                Mosaic.raw(orgId, userId, UUID.fromString(p0), z, x, y)},
              'LC8_1 -> { (z: Int, x: Int, y: Int) =>
                Mosaic.raw(orgId, userId, UUID.fromString(p1), z, x, y)})

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
                val png = tile.renderPng(lookupColorMap(colorMap))
                pngAsHttpResponse(png)
              }
            }
          }
        }
      }
    }
}
