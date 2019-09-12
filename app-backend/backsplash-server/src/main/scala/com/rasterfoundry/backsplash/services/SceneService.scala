package com.rasterfoundry.backsplash.server

import com.rasterfoundry.backsplash._
import com.rasterfoundry.common.color.ColorCorrect
import com.rasterfoundry.backsplash.Parameters._
import com.rasterfoundry.backsplash.RenderableStore.ToRenderableStoreOps
import com.rasterfoundry.backsplash.error._
import com.rasterfoundry.datamodel.{BandOverride, Datasource, Scene}
import com.rasterfoundry.database.DatasourceDao
import cats.data.OptionT
import cats.data.Validated._
import cats.effect._
import cats.implicits._
import doobie.implicits._
import doobie.util.transactor.Transactor
import geotrellis.raster.CellSize
import geotrellis.server._
import geotrellis.vector.{MultiPolygon, Projected}
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.headers._
import java.util.UUID

import com.colisweb.tracing.TracingContext
import com.colisweb.tracing.TracingContext.TracingContextBuilder
import com.rasterfoundry.http4s.{TracedHTTPRoutes, AuthedTraceRequest}

class SceneService[RendStore, HistStore](
    mosaicImplicits: MosaicImplicits[HistStore, RendStore],
    xa: Transactor[IO])(implicit cs: ContextShift[IO],
                        builder: TracingContextBuilder[IO])
    extends ToRenderableStoreOps {

  import mosaicImplicits._

  implicit val tmsReification = paintedMosaicTmsReification

  implicit val sceneCache = Cache.caffeineSceneCache

  private def sceneToBacksplashGeotiff(
      scene: Scene,
      bandOverride: Option[BandOverride],
      tracingContext: TracingContext[IO]): IO[BacksplashImage[IO]] = {
    val randomProjectId = UUID.randomUUID
    val ingestLocIO: IO[String] = IO {
      scene.ingestLocation
    } flatMap {
      case Some(loc) =>
        IO.pure {
          loc
        }
      case None =>
        IO.raiseError(
          UningestedScenesException(s"Scene ${scene.id} is not ingested"))
    }
    val footprintIO: IO[Projected[MultiPolygon]] = IO {
      scene.dataFootprint
    } flatMap {
      case Some(footprint) =>
        IO.pure {
          footprint
        }
      case None => IO.raiseError(NoFootprintException)
    }
    val imageBandOverride = bandOverride map { ovr =>
      List(ovr.redBand, ovr.greenBand, ovr.blueBand)
    } getOrElse {
      List(0, 1, 2)
    }
    (ingestLocIO, footprintIO).tupled map {
      case (ingestLocation, footprint) =>
        BacksplashGeotiff(
          scene.id,
          randomProjectId,
          randomProjectId,
          ingestLocation,
          imageBandOverride,
          ColorCorrect.paramsFromBandSpecOnly(0, 1, 2),
          None, // no single band options ever
          None, // not adding the mask here, since out of functional scope for md to image
          footprint,
          scene.metadataFields,
          tracingContext
        )
    }
  }

  private def getDefaultSceneBands(
      sceneId: UUID): IO[Fiber[IO, Option[BandOverride]]] = {
    (OptionT {
      DatasourceDao
        .getSceneDatasource(sceneId)
        .transact(xa)
    } map { (ds: Datasource) =>
      ds.defaultColorComposite map {
        _.value
      } getOrElse {
        // default case is we couldn't get a color composite from:
        // a composite with natural in the name
        // a composite with default in the name
        // or the names of the bands on the datasource
        BandOverride(0, 1, 2)
      }
    }).value.start
  }

  private val pngType = `Content-Type`(MediaType.image.png)

  val authorizers = new Authorizers(xa)

  val routes =
    TracedHTTPRoutes[IO] {
      case AuthedTraceRequest(authedRequest, tracingContext) => {
        authedRequest match {
          case GET -> Root / UUIDWrapper(sceneId) / IntVar(z) / IntVar(x) / IntVar(
                y) as user =>
            for {
              sceneFiber <- authorizers.authScene(user, sceneId).start
              bandsFiber <- getDefaultSceneBands(sceneId)
              scene <- sceneFiber.join.handleErrorWith { error =>
                bandsFiber.cancel *> IO.raiseError(error)
              }
              bands <- bandsFiber.join
              eval = LayerTms.identity(
                sceneToBacksplashGeotiff(scene, bands, tracingContext).map(a =>
                  List(a)))
              resp <- eval(z, x, y) flatMap {
                case Valid(tile) =>
                  Ok(tile.renderPng.bytes, pngType)
                case Invalid(e) =>
                  BadRequest(s"Could not produce tile: $e")
              }
            } yield resp

          case GET -> Root / UUIDWrapper(sceneId) / "thumbnail"
                :? ThumbnailQueryParamDecoder(thumbnailSize) as user =>
            for {
              sceneFiber <- authorizers.authScene(user, sceneId).start
              bandsFiber <- getDefaultSceneBands(sceneId)
              scene <- sceneFiber.join.handleErrorWith { error =>
                bandsFiber.cancel *> IO.raiseError(error)
              }
              bands <- bandsFiber.join
              eval = LayerExtent.identity(
                sceneToBacksplashGeotiff(scene, bands, tracingContext).map(a =>
                  List(a)))(paintedMosaicExtentReification, cs)
              extent <- IO.pure {
                (scene.dataFootprint orElse scene.tileFootprint) map {
                  _.envelope
                }
              } flatMap {
                case Some(extent) =>
                  IO.pure {
                    extent
                  }
                case None => IO.raiseError(NoFootprintException)
              }
              xSize = extent.width / thumbnailSize.width
              ySize = extent.height / thumbnailSize.height
              resp <- eval(extent, CellSize(xSize, ySize)) flatMap {
                case Valid(tile) =>
                  Ok(tile.renderPng.bytes, pngType)
                case Invalid(e) =>
                  BadRequest(s"Could not produce tile: $e")
              }
            } yield resp
        }
      }
    }
}
