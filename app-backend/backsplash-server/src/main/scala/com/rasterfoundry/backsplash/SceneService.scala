package com.rasterfoundry.backsplash.server

import com.rasterfoundry.backsplash._
import com.rasterfoundry.backsplash.Parameters._
import com.rasterfoundry.backsplash.ProjectStore.ToProjectStoreOps
import com.rasterfoundry.backsplash.error._
import com.rasterfoundry.datamodel.User

import cats.Applicative
import cats.data.Validated._
import cats.effect._
import cats.implicits._
import doobie.util.transactor.Transactor
import geotrellis.server._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.headers._

import java.util.UUID

class SceneService[ProjStore: ProjectStore, HistStore: HistogramStore](
    scenes: ProjStore,
    mtr: MetricsRegistrator,
    mosaicImplicits: MosaicImplicits[HistStore],
    histStore: HistStore,
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
                :? RedBandOptionalQueryParamMatcher(redOverride)
                :? GreenBandOptionalQueryParamMatcher(greenOverride)
                :? BlueBandOptionalQueryParamMatcher(blueOverride) as user =>
            val bandOverride =
              Applicative[Option].map3(redOverride,
                                       greenOverride,
                                       blueOverride)(BandOverride.apply)
            val eval =
              LayerTms.identity(scenes.read(sceneId, None, bandOverride, None))
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
        }
      }
    }
}
