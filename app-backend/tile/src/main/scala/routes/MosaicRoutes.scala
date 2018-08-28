package com.azavea.rf.tile.routes

import com.azavea.rf.common.RfStackTrace
import com.azavea.rf.tile._
import com.azavea.rf.tile.image.Mosaic
import com.azavea.rf.datamodel.{ColorCorrect, SceneToProject}
import com.azavea.rf.database.Implicits._
import com.azavea.rf.database.{SceneToProjectDao}
import com.azavea.rf.database.util.RFTransactor

import geotrellis.raster._
import geotrellis.raster.io.geotiff._
import geotrellis.raster.render.Png
import geotrellis.proj4._
import geotrellis.vector.{Extent, Projected}
import geotrellis.raster.histogram._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.ParameterDirectives.parameters
import akka.http.scaladsl.model.{
  ContentType,
  HttpEntity,
  HttpResponse,
  MediaTypes,
  StatusCodes
}
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import cats.data.OptionT
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.Fragments.in
import doobie.postgres._
import doobie.postgres.implicits._
import cats.effect.IO

import scala.concurrent._
import scala.util._
import scala.collection.mutable.ArrayBuffer
import java.util.UUID

object MosaicRoutes extends LazyLogging with KamonTrace {
  val system = AkkaSystem.system
  implicit val blockingDispatcher =
    system.dispatchers.lookup("blocking-dispatcher")
  implicit lazy val xa = RFTransactor.xa

  val emptyTilePng = IntArrayTile.ofDim(256, 256).renderPng

  def pngAsHttpResponse(png: Png): HttpResponse =
    HttpResponse(
      entity = HttpEntity(ContentType(MediaTypes.`image/png`), png.bytes))

  def tiffAsHttpResponse(tiff: MultibandGeoTiff): HttpResponse =
    HttpResponse(
      entity =
        HttpEntity(ContentType(MediaTypes.`image/tiff`), tiff.toByteArray))

  def mosaicQueryParameters = parameters(
    'redBand.as[Int].?,
    'greenBand.as[Int].?,
    'blueBand.as[Int].?
  )

  def mosaicProject(projectId: UUID)(implicit xa: Transactor[IO]): Route =
    pathPrefix("export") {
      optionalHeaderValueByName("Accept") { acceptContentType =>
        traceName("tiles-export-request") {
          parameter("bbox", "zoom".as[Int] ?, "colorCorrect".as[Boolean] ? true) {
            (bbox, zoom, colorCorrect) =>
              get {
                complete {
                  val future = acceptContentType match {
                    case Some("image/tiff") =>
                      val mosaic = Mosaic.render(projectId,
                                                 zoom,
                                                 Option(bbox),
                                                 colorCorrect)
                      val poly =
                        Projected(Extent.fromString(bbox).toPolygon(), 4326)
                          .reproject(LatLng, WebMercator)(3857)
                      val extent = poly.envelope
                      mosaic
                        .map { m =>
                          MultibandGeoTiff(m, extent, WebMercator)
                        }
                        .map(tiffAsHttpResponse)
                        .value
                    case _ =>
                      Mosaic
                        .render(projectId, zoom, Option(bbox), colorCorrect)
                        .map(_.renderPng)
                        .map(pngAsHttpResponse)
                        .value
                  }

                  future onComplete {
                    case Success(s) => s
                    case Failure(e) =>
                      logger.error(
                        s"Message: ${e.getMessage}\nStack trace: ${RfStackTrace(e)}")
                  }

                  future
                }
              }
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
        parameters(
          'redBand.as[Int].?,
          'greenBand.as[Int].?,
          'blueBand.as[Int].?
        ) { (redband, greenBand, blueBand) =>
          complete {
            val mosaic = (redband, greenBand, blueBand) match {
              case (Some(red), Some(green), Some(blue)) =>
                Mosaic(projectId, zoom, x, y, red, green, blue)
              case _ => Mosaic(projectId, zoom, x, y)
            }
            val future = mosaic
              .map(_.renderPng)
              .getOrElse(emptyTilePng)
              .map(pngAsHttpResponse)

            future onComplete {
              case Success(s) => s
              case Failure(e) =>
                logger.error(
                  s"Message: ${e.getMessage}\nStack trace: ${RfStackTrace(e)}")
            }

            future
          }
        }
      }
    }

  def mosaicScene(sceneId: UUID)(implicit xa: Transactor[IO]): Route =
    pathPrefix(IntNumber / IntNumber / IntNumber) { (zoom, x, y) =>
      get {
        complete {
          val future =
            timedFuture("tile-zxy")(
              Mosaic(sceneId, zoom, x, y, true)
                .map(_.renderPng)
                .getOrElse(emptyTilePng)
                .map(pngAsHttpResponse)
            )
          future onComplete {
            case Success(s) => s
            case Failure(e) =>
              logger.error(
                s"Message: ${e.getMessage}\nStack trace: ${RfStackTrace(e)}")
          }
          future
        }
      }
    }

  /** Return the histogram (with color correction applied) for a list of scenes in a project */
  def getProjectScenesHistogram(projectId: UUID)(
      implicit xa: Transactor[IO]): Route = {
    def correctedHistograms(
        sceneId: UUID,
        projectId: UUID): OptionT[Future, Vector[Histogram[Int]]] = {
      val tileFuture: OptionT[Future, MultibandTile] = StitchLayer(sceneId, 64)
      val ccParamF: OptionT[Future, ColorCorrect.Params] = OptionT(
        SceneToProjectDao
          .getColorCorrectParams(projectId, sceneId)
          .transact(xa)
          .unsafeToFuture
          .map(_.some))

      for {
        tile <- tileFuture
        params <- ccParamF
      } yield {
        val (rgbBands, rgbHist) =
          params.reorderBands(tile, tile.histogramDouble)
        val sceneBands = ColorCorrect(rgbBands, rgbHist, params).bands
        sceneBands.map(tile => tile.histogram)
      }
    }

    entity(as[Seq[UUID]]) { sceneIds =>
      complete {
        SceneToProjectDao.query
          .filter(fr"project_id=${projectId}")
          .list
          .transact(xa)
          .unsafeToFuture
          .flatMap { stps: List[SceneToProject] =>
            val vSceneIds: List[UUID] = stps.map(_.sceneId)
            // if (sceneIds.forall(vSceneIds.contains(_))) {
            // Verify that scenes POSTed to the endpoint are in the project
            val histograms: Seq[Future[Option[Vector[Histogram[Int]]]]] =
              sceneIds.map(correctedHistograms(_, projectId).value)
            val scenesHistograms = Future.sequence(
              histograms
            )

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
                hist.foreach { (value, count) =>
                  valCounts += Tuple2(value, count)
                }
                valCounts.toMap
              })
            }

            mergedBandHistograms onComplete {
              case Success(s) => s
              case Failure(e) =>
                logger.error(
                  s"Message: ${e.getMessage}\nStack trace: ${RfStackTrace(e)}")
            }
            mergedBandHistograms
          // } else {
          //   Future { HttpResponse(400, entity = "At least one scene in the request is not in the project") }
          // }
          }
      }
    }
  }
}
