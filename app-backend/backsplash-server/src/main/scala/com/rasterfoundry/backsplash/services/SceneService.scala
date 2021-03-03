package com.rasterfoundry.backsplash.server

import com.rasterfoundry.backsplash.Parameters._
import com.rasterfoundry.backsplash.Parameters._
import com.rasterfoundry.backsplash.RenderableStore.ToRenderableStoreOps
import com.rasterfoundry.backsplash._
import com.rasterfoundry.backsplash.error._
import com.rasterfoundry.common.color.ColorCorrect
import com.rasterfoundry.database.DatasourceDao
import com.rasterfoundry.datamodel.{BandOverride, Datasource, Scene}
import com.rasterfoundry.http4s.TracedHTTPRoutes
import com.rasterfoundry.http4s.TracedHTTPRoutes._

import cats.data.OptionT
import cats.data.Validated._
import cats.effect._
import cats.implicits._
import com.colisweb.tracing.core.TracingContextBuilder
import doobie.implicits._
import doobie.util.transactor.Transactor
import geotrellis.raster.CellSize
import geotrellis.server._
import geotrellis.vector._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.headers._

import java.util.UUID

class SceneService[HistStore](
    mosaicImplicits: MosaicImplicits[HistStore],
    xa: Transactor[IO]
)(implicit cs: ContextShift[IO], builder: TracingContextBuilder[IO])
    extends ToRenderableStoreOps {

  import mosaicImplicits._

  implicit val tmsReification = paintedMosaicTmsReification

  implicit val sceneCache = Cache.caffeineSceneCache

  private def sceneToBacksplashGeotiff(
      scene: Scene,
      bandOverride: Option[BandOverride]
  ): IO[BacksplashImage[IO]] = {
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
          UningestedScenesException(s"Scene ${scene.id} is not ingested")
        )
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
          ColorCorrect.Params(0, 1, 2),
          None, // no single band options ever
          None, // not adding the mask here, since out of functional scope for md to image
          footprint,
          scene.metadataFields,
          xa
        )
    }
  }

  private def getDefaultSceneBands(
      sceneId: UUID
  ): IO[Fiber[IO, Option[BandOverride]]] = {
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
      case GET -> Root / UUIDWrapper(sceneId) / IntVar(z) / IntVar(x) / IntVar(
            y
          ) :? BandOverrideQueryParamDecoder(
            bandOverride
          ) as user using tracingContext =>
        for {
          sceneFiber <- authorizers.authScene(user, sceneId).start
          bandsFiber <- getDefaultSceneBands(sceneId)
          scene <- sceneFiber.join.handleErrorWith { error =>
            bandsFiber.cancel *> IO.raiseError(error)
          }
          bands <- bandsFiber.join
          eval = LayerTms.identity(
            sceneToBacksplashGeotiff(
              scene,
              bandOverride orElse bands
            ).map(a => (tracingContext, List(a)))
          )
          resp <- eval(z, x, y) flatMap {
            case Valid(tile) =>
              Ok(tile.renderPng.bytes, pngType)
            case Invalid(e) =>
              BadRequest(s"Could not produce tile: $e")
          }
        } yield resp

      case GET -> Root / UUIDWrapper(sceneId) / "thumbnail"
            :? ThumbnailQueryParamDecoder(
              thumbnailSize
            ) as user using tracingContext =>
        for {
          sceneFiber <- authorizers.authScene(user, sceneId).start
          bandsFiber <- getDefaultSceneBands(sceneId)
          scene <- sceneFiber.join.handleErrorWith { error =>
            bandsFiber.cancel *> IO.raiseError(error)
          }
          bands <- bandsFiber.join
          eval = LayerExtent.identity(
            sceneToBacksplashGeotiff(scene, bands)
              .map(a => (tracingContext, List(a)))
          )(paintedMosaicExtentReification, cs)
          extent <- IO.pure {
            (scene.dataFootprint orElse scene.tileFootprint) map {
              _.geom.extent
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
