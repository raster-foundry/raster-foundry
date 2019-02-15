package com.rasterfoundry.backsplash.server

import com.rasterfoundry.backsplash._
import com.rasterfoundry.backsplash.Parameters._
import com.rasterfoundry.backsplash.ProjectStore.ToProjectStoreOps
import com.rasterfoundry.backsplash.error._
import com.rasterfoundry.common.datamodel.{BandOverride, User}
import com.rasterfoundry.common.utils.TileUtils
import com.rasterfoundry.database.SceneDao

import cats.data.Validated._
import cats.effect._
import cats.implicits._
import doobie.implicits._
import doobie.util.transactor.Transactor
import geotrellis.raster.CellSize
import geotrellis.server._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.headers._

class SceneService[ProjStore: ProjectStore, HistStore](
    scenes: ProjStore,
    mosaicImplicits: MosaicImplicits[HistStore],
    xa: Transactor[IO])(implicit cs: ContextShift[IO],
                        H: HttpErrorHandler[IO, BacksplashException, User],
                        ForeignError: HttpErrorHandler[IO, Throwable, User])
    extends ToProjectStoreOps {

  import mosaicImplicits._
  implicit val tmsReification = paintedMosaicTmsReification

  private val pngType = `Content-Type`(MediaType.image.png)

  val authorizers = new Authorizers(xa)

  val routes: AuthedService[User, IO] =
    H.handle {
      ForeignError.handle {
        AuthedService {
          case GET -> Root / UUIDWrapper(sceneId) / IntVar(z) / IntVar(x) / IntVar(
                y)
                :? BandOverrideQueryParamDecoder(bandOverride) as user =>
            val bbox = TileUtils.getTileBounds(z, x, y)
            val eval =
              LayerTms.identity(
                scenes.read(sceneId, Some(bbox), bandOverride, None))
            for {
              fiberAuth <- authorizers.authScene(user, sceneId).start
              fiberResp <- eval(z, x, y).start
              _ <- fiberAuth.join.handleErrorWith { error =>
                fiberResp.cancel *> IO.raiseError(error)
              }
              resp <- fiberResp.join flatMap {
                case Valid(tile) =>
                  Ok(tile.renderPng.bytes, pngType)
                case Invalid(e) =>
                  BadRequest(s"Could not produce tile: $e")
              }
            } yield resp

          case GET -> Root / UUIDWrapper(sceneId) / "thumbnail"
                :? BandOverrideQueryParamDecoder(bandOverride)
                :? ThumbnailQueryParamDecoder(thumbnailSize) as user =>
            def getEval(ovr: BandOverride) =
              LayerExtent.identity(scenes.read(sceneId, None, Some(ovr), None))
            for {
              authFiber <- authorizers.authScene(user, sceneId).start
              bandsFiber <- bandOverride match {
                case Some(b) => IO.pure(b).start
                case None =>
                  SceneDao
                    .getSceneDatasource(sceneId)
                    .transact(xa)
                    .map(
                      _.defaultColorComposite map { _.value } getOrElse {
                        BandOverride(0, 1, 2)
                      }
                    )
                    .start
              }
              footprintFiber <- SceneDao
                .unsafeGetSceneById(sceneId)
                .transact(xa)
                .map(scene => scene.dataFootprint orElse scene.tileFootprint)
                .start
              _ <- authFiber.join.handleErrorWith { error =>
                bandsFiber.cancel *> footprintFiber.cancel *> IO.raiseError(
                  error)
              }
              (width, height) = thumbnailSize
              footprint <- footprintFiber.join
              bands <- bandsFiber.join
              eval = getEval(bands)
              extent = footprint.get.envelope
              xSize = extent.width / width
              ySize = extent.height / height
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
