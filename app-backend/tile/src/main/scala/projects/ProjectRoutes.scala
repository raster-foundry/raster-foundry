package com.azavea.rf.tile.projects

import java.util.UUID

import com.azavea.rf.common.{KamonTraceRF, RfStackTrace}
import com.azavea.rf.database.Database
import com.azavea.rf.database.tables.ScenesToProjects
import com.azavea.rf.datamodel.ColorCorrect
import com.azavea.rf.tile._
import com.azavea.rf.tile.project._

import akka.dispatch.MessageDispatcher
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.server._
import cats.data.OptionT
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import geotrellis.proj4._
import geotrellis.raster._
import geotrellis.raster.io.geotiff._
import geotrellis.slick.Projected
import geotrellis.vector.Extent

import scala.collection.mutable.ArrayBuffer
import scala.concurrent._
import scala.util._

trait ProjectRoutes extends LazyLogging with KamonTraceRF
  with TileAuthentication with ProjectMosaic {

  implicit val blockingDispatcher: MessageDispatcher
  implicit val database: Database

  val emptyTilePng = IntArrayTile.ofDim(256, 256).renderPng

  val exportDirectives = {
    optionalHeaderValueByName("Accept") &
      parameter("bbox", "zoom".as[Int] ?, "colorCorrect".as[Boolean] ? true) &
      get
  }

  val projectRoutes: Route = pathPrefix(JavaUUID) { projectId =>
    tileAccessAuthorized(projectId) {
      case false => reject(AuthorizationFailedRejection)
      case true => {
        pathPrefix("export") {
          exportDirectives { (acceptContentType, bbox, zoom, colorCorrect) =>
            complete {
              traceName("tiles-export-request") {
                getProjectExtentTile(projectId, acceptContentType, bbox, zoom, colorCorrect)
              }
            }
          }
        } ~ pathPrefix("histogram") {
          post {
            traceName("tiles-project-histogram") {
              getProjectScenesHistogram(projectId)
            }
          }
        } ~ pathPrefix(IntNumber / IntNumber / IntNumber) { (zoom, x, y) =>
          get {
            complete {
              getProjectTile(projectId, zoom, x, y)
            }
          }
        }
      }
    }
  }

  def getProjectTile(projectId: UUID, zoom: Int, x: Int, y: Int) = {
    val png = getProjectMosaicByZXY(projectId, zoom, x, y)
        .map(_.renderPng)
        .getOrElse(emptyTilePng)
    png onComplete {
      case Success(s) => s
      case Failure(e) =>
        logger.error(s"Message: ${e.getMessage}\nStack trace: ${RfStackTrace(e)}")
    }
    png
  }

  def getProjectExtentTile(projectId: UUID, acceptContentType: Option[String], bbox: String, zoom: Option[Int], colorCorrect: Boolean): ToResponseMarshallable = {
    val mosaic = getProjectMosaicByExtent(projectId, zoom, Option(bbox), colorCorrect)
    val poly = Projected(Extent.fromString(bbox).toPolygon(), 4326)
      .reproject(LatLng, WebMercator)(3857)
    val extent = poly.envelope
    acceptContentType match {
      case Some("image/tiff") => mosaic.map(MultibandGeoTiff(_, extent, WebMercator)).value
      case _ => mosaic.map(_.renderPng).value
    }
  }

  /** Return the histogram (with color correction applied) for a list of scenes in a project */
  def getProjectScenesHistogram(projectId: UUID)(implicit database: Database): Route = {
    entity(as[Array[UUID]]) { sceneIds =>
      val scenesHistograms = Future.sequence(sceneIds
        .map(getCorrectedHistogram(_, projectId)) // Array[OptionT]
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
        val future = mergedBandHistograms

        future onComplete {
          case Success(s) => s
          case Failure(e) =>
            logger.error(s"Message: ${e.getMessage}\nStack trace: ${RfStackTrace(e)}")
        }

        future
      }
    }
  }
}
