package com.azavea.rf.tile.routes

import cats.data.OptionT
import cats.implicits._

import com.azavea.rf.tile._
import com.azavea.rf.tile.image._
import com.azavea.rf.database.Database
import com.azavea.rf.database.tables.ScenesToProjects
import com.azavea.rf.datamodel.ColorCorrect

import geotrellis.raster._
import geotrellis.raster.io.geotiff._
import geotrellis.raster.render.Png
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.{ContentType, HttpEntity, HttpResponse, MediaTypes}
import akka.http.scaladsl.unmarshalling._
import com.typesafe.scalalogging.LazyLogging
import cats.implicits._
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import scala.collection.mutable.ArrayBuffer

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import java.util.UUID

import akka.http.scaladsl.marshalling.Marshaller
import geotrellis.proj4._
import geotrellis.slick.Projected
import geotrellis.vector.{Extent, Polygon}

object MosaicRoutes extends LazyLogging {

  val emptyTilePng = IntArrayTile.ofDim(256, 256).renderPng

  def pngAsHttpResponse(png: Png): HttpResponse =
    HttpResponse(entity = HttpEntity(ContentType(MediaTypes.`image/png`), png.bytes))

  def tiffAsHttpResponse(tiff: MultibandGeoTiff): HttpResponse =
    HttpResponse(entity = HttpEntity(ContentType(MediaTypes.`image/tiff`), tiff.toByteArray))

  def mosaicProject(projectId: UUID)(implicit database: Database): Route =
    pathPrefix("export") {
      optionalHeaderValueByName("Accept") { acceptContentType =>
        parameter("bbox", "zoom".as[Int]?, "colorCorrect".as[Boolean] ? true) {
          (bbox, zoom, colorCorrect) => get {
            complete {
              val mosaic = Mosaic.render(projectId, zoom, Option(bbox), colorCorrect)
              val poly = Projected(Extent.fromString(bbox).toPolygon(), 4326)
                .reproject(LatLng, WebMercator)(3857)
              val extent = poly.envelope
              acceptContentType match {
                case Some("image/tiff") => mosaic
                    .map{ m => MultibandGeoTiff(m, extent, WebMercator) }
                    .map(tiffAsHttpResponse)
                    .value
                case _ => mosaic.map(_.renderPng)
                    .map(pngAsHttpResponse)
                    .value
              }
            }
          }
        }
      }
    } ~ pathPrefix("histogram") {
      post { getProjectScenesHistogram(projectId) }
    } ~ pathPrefix (IntNumber / IntNumber / IntNumber ) { (zoom, x, y) =>
      parameter("tag".?) { tag =>
        get {
          complete {
            Mosaic(projectId, zoom, x, y, tag)
              .map(_.renderPng)
              .getOrElse(emptyTilePng)
              .map(pngAsHttpResponse)
          }
        }
      }
    }

  /** Return the histogram (with color correction applied) for a list of scenes in a project */
  def getProjectScenesHistogram(projectId: UUID)(implicit database: Database): Route = {
    def correctedHistograms(sceneId: UUID, projectId: UUID) = {
      val tileFuture = StitchLayer(sceneId, 64)
      // getColorCorrectParams returns a Future[Option[Option]] for some reason
      val ccParamFuture = ScenesToProjects.getColorCorrectParams(projectId, sceneId).map { _.flatten }
      for {
        tile <- tileFuture
        params <- OptionT(ccParamFuture)
      } yield {
        val (rgbBands, rgbHist) = params.reorderBands(tile, tile.histogramDouble)
        val sceneBands = ColorCorrect(rgbBands, rgbHist, params).bands
        sceneBands.map(tile => tile.histogram)
      }
    }
    entity(as[Array[UUID]]) { sceneIds =>
      val scenesHistograms = Future.sequence(sceneIds
        .map(correctedHistograms(_, projectId)) // Array[OptionT]
        .map(_.value).toList                    // Array[Future[Option[...]]]
      )                                         // Future[Array[Option[...]]]

      val mergedBandHistograms = scenesHistograms.map { scenes =>
        // We need to switch this from a list of histograms by tile, to a list of histograms by band,
        // hence the transpose call.
        // Then reduce each band down to a single histogram (still leaving us with an array of histograms,
        // one for each band).
        val mergedHists = scenes.flatten.transpose.map { bandHists =>
          bandHists.reduceLeft((lHist, rHist) => lHist.merge(rHist))
        }
        // Turn histograms into value -> count mapping.
        // TODO: This is more complicated than it needs to be because there's only one way to map over
        // a histogram's (val, count) pairs.
        mergedHists.map(hist => {
          val valCounts = ArrayBuffer[(Int, Long)]()
          hist.foreach { (value, count) => valCounts += Tuple2(value, count) }
          valCounts.toMap
        })
      }
      complete {
        mergedBandHistograms
      }
    }
  }
// TODO: re-enable this, needed for project color correction endpoint
//   def mosaicScenes: Route =
//     pathPrefix(JavaUUID / Segment / "mosaic" / IntNumber / IntNumber / IntNumber) { (orgId, userId, zoom, x, y) =>
//       colorCorrectParams { params =>
//         parameters('scene.*) { scenes =>
//           get {
//             complete {
//               val ids = scenes.map(id => RfLayerId(orgId, userId, UUID.fromString(id)))
//               Mosaic(params, ids, zoom, x, y).map { maybeTile =>
//                 maybeTile.map { tile => pngAsHttpResponse(tile.renderPng())}
//               }
//             }
//           }
//         }
//       }
//     }
}
