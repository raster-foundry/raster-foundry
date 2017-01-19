package com.azavea.rf.tile.routes

import com.azavea.rf.tile.image._
import com.azavea.rf.database.Database

import geotrellis.raster._
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

object ToolRoutes extends LazyLogging {
  val userId: String = "rf_airflow-user"

  def ndviDiff: Op.Tree = {
    val red = Op('red)
    val nir = Op('nir)
    val ndvi = (nir - red) / (nir + red)

    val params0 = Map(
      Op.Var('red) -> Op("LC8_1[3]"),
      Op.Var('nir) -> Op("LC8_1[4]")
    )

    val params1 = Map(
      Op.Var('red) -> Op("LC8_0[3]"),
      Op.Var('nir) -> Op("LC8_0[4]")
    )

    val maskThreshold = 0.01
    val mask: Double => Double = { d: Double => if (d > maskThreshold) 1.0 else 0.0 }
    Op.MapDouble(ndvi.bind(params0) - ndvi.bind(params1), mask)
  }

  type TMS = (Int, Int, Int) => Future[Option[MultibandTile]]

  def varParams(orgId: UUID)(implicit db: Database): Directive1[Map[Symbol, TMS]] =
    parameters('LC8_0, 'LC8_1).as { (p0: String, p1: String) =>
      Map(
        'LC8_0 -> { (z: Int, x: Int, y: Int) =>
          Mosaic(orgId, userId, UUID.fromString(p0), z, x, y, None, false)},
        'LC8_1 -> { (z: Int, x: Int, y: Int) =>
          Mosaic(orgId, userId, UUID.fromString(p1), z, x, y, None, false)})
    }

  // TODO: refactor; there should an awesome way to navigate the op tree without having to match
  def lookupModel: PartialFunction[Option[String], Op] = {
    case None =>
      ndviDiff
    case Some("ndvi0") =>
      ndviDiff.left.left
    case Some("ndvi1") =>
      ndviDiff.left.right
  }

  def lookupColorMap: PartialFunction[Option[String], ColorMap] = {
    case _ =>
      val colorRamp = ColorRamp(Array[Int](0xffffe5aa, 0xf7fcb9ff, 0xd9f0a3ff, 0xaddd8eff, 0x78c679ff, 0x41ab5dff, 0x238443ff, 0x006837ff, 0x004529ff))
      val breaks = Array[Double](0.05, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 1.0)
      ColorMap(breaks, colorRamp)
  }

  def pngAsHttpResponse(png: Png): HttpResponse =
    HttpResponse(entity = HttpEntity(ContentType(MediaTypes.`image/png`), png.bytes))

  def root(implicit db: Database) =
    pathPrefix(Segment / "ndvi-diff-tool"){ organizationId =>
      (pathEndOrSingleSlash & get & rejectEmptyResponse) {
        complete("model JSON")
      } ~
      pathPrefix(IntNumber / IntNumber / IntNumber){ (z, x, y) =>
        (varParams(UUID.fromString(organizationId)) & parameter('part.?)) { (varMap, partId) =>
          complete {
            val model: Op = lookupModel(partId)
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
                val png = tile.renderPng(lookupColorMap(partId))
                pngAsHttpResponse(png)
              }
            }
          }
        }
      }
    }
}
