package com.rasterfoundry.backsplash.server

import com.rasterfoundry.backsplash._
import com.rasterfoundry.backsplash.Parameters._
import com.rasterfoundry.backsplash.RenderableStore.ToRenderableStoreOps
import com.rasterfoundry.backsplash.error._
import com.rasterfoundry.datamodel.{BandOverride, User}
import com.rasterfoundry.common.utils.TileUtils
import com.rasterfoundry.database.SceneDao

import cats.data.Validated._
import cats.effect._
import cats.implicits._
import doobie.implicits._
import doobie.util.transactor.Transactor
import geotrellis.raster.CellSize
import geotrellis.vector._
import geotrellis.server._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.headers._

import java.util.UUID

class SceneService[RendStore: RenderableStore, HistStore](
    scenes: RendStore,
    mosaicImplicits: MosaicImplicits[HistStore, RendStore],
    xa: Transactor[IO])(implicit cs: ContextShift[IO])
    extends ToRenderableStoreOps {

  import mosaicImplicits._
  implicit val tmsReification = paintedMosaicTmsReification

  private def getDefaultSceneBands(
      sceneId: UUID): IO[Fiber[IO, BandOverride]] = {
    SceneDao
      .getSceneDatasource(sceneId)
      .transact(xa)
      .map(
        _.defaultColorComposite map { _.value } getOrElse {
          // default case is we couldn't get a color composite from:
          // a composite with natural in the name
          // a composite with default in the name
          // or the names of the bands on the datasource
          BandOverride(0, 1, 2)
        }
      )
      .start
  }

  private def getSceneFootprint(
      sceneId: UUID): IO[Fiber[IO, Option[Projected[MultiPolygon]]]] =
    SceneDao
      .unsafeGetSceneById(sceneId)
      .transact(xa)
      .map(scene => scene.dataFootprint orElse scene.tileFootprint)
      .start

  private val pngType = `Content-Type`(MediaType.image.png)

  val authorizers = new Authorizers(xa)

  val routes: AuthedService[User, IO] =
    AuthedService {
      case GET -> Root / UUIDWrapper(sceneId) / IntVar(z) / IntVar(x) / IntVar(
            y) as user =>
        val bbox = TileUtils.getTileBounds(z, x, y)
        for {
          authFiber <- authorizers.authScene(user, sceneId).start
          bandsFiber <- getDefaultSceneBands(sceneId)
          _ <- authFiber.join.handleErrorWith { error =>
            bandsFiber.cancel *> IO.raiseError(error)
          }
          bands <- bandsFiber.join
          eval = LayerTms.identity(
            scenes.read(sceneId, Some(bbox), Some(bands), None))
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
          authFiber <- authorizers.authScene(user, sceneId).start
          bandsFiber <- getDefaultSceneBands(sceneId)
          footprintFiber <- getSceneFootprint(sceneId)
          _ <- authFiber.join.handleErrorWith { error =>
            bandsFiber.cancel *> footprintFiber.cancel *> IO.raiseError(error)
          }
          footprint <- footprintFiber.join
          bands <- bandsFiber.join
          eval = LayerExtent.identity(
            scenes.read(sceneId, None, Some(bands), None))(
            paintedMosaicExtentReification,
            cs)
          extent = footprint map { _.envelope } getOrElse {
            throw NoFootprintException
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
