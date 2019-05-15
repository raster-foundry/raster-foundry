package com.rasterfoundry.backsplash

import com.rasterfoundry.common.ast.{
  MapAlgebraAST,
  NodeMetadata,
  MamlConversion
}
import com.rasterfoundry.backsplash.error._
import com.rasterfoundry.database.ProjectDao

import com.azavea.maml.ast._
import com.azavea.maml.util.{ClassMap => _}

import cats.effect.IO
import doobie.Transactor
import doobie.implicits._

class BacksplashMamlAdapter[HistStore, LayerStore: ProjectStore](
    mosaicImplicits: MosaicImplicits[HistStore],
    layerStore: LayerStore,
    xa: Transactor[IO]) {
  import mosaicImplicits._

  def asMaml(ast: MapAlgebraAST)
    : (Expression, Option[NodeMetadata], Map[String, BacksplashMosaic]) = {

    def evalParams(ast: MapAlgebraAST): Map[String, BacksplashMosaic] = {
      val args = ast.args.map(evalParams)

      ast match {
        case MapAlgebraAST.ProjectRaster(_, projId, band, _, _) => {
          val bandActual = band.getOrElse(
            throw SingleBandOptionsException(
              "Band must be provided to evaluate AST"))
          val mosaic = fs2.Stream.eval {
            ProjectDao.unsafeGetProjectById(projId).transact(xa)
          } flatMap { project =>
            layerStore.read(project.defaultLayerId, None, None, None)
          } map { backsplashImage =>
            backsplashImage.selectBands(List(bandActual))
          }
          Map[String, BacksplashMosaic](
            s"${projId.toString}_${bandActual}" -> mosaic
          )
        }

        case MapAlgebraAST.LayerRaster(_, layerId, band, _, _) => {
          val bandActual = band.getOrElse(
            throw SingleBandOptionsException(
              "Band must be provided to evaluate AST")
          )
          Map[String, BacksplashMosaic](
            s"${layerId.toString}_${bandActual}" -> (
              layerStore
                .read(layerId, None, None, None) map { backsplashIm =>
                backsplashIm.selectBands(List(bandActual))
              }
            )
          )
        }
        case _ =>
          args.foldLeft(Map.empty[String, BacksplashMosaic])((a, b) => a ++ b)
      }
    }

    (MamlConversion.fromDeprecatedAST(ast), ast.metadata, evalParams(ast))
  }
}
