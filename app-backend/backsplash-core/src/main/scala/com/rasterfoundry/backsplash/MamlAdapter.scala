package com.rasterfoundry.backsplash

import com.rasterfoundry.common.ast.{
  MapAlgebraAST,
  NodeMetadata,
  MamlConversion
}
import com.rasterfoundry.backsplash.error._

import com.azavea.maml.ast._
import com.azavea.maml.util.{ClassMap => _}

class BacksplashMamlAdapter[HistStore: HistogramStore, ProjStore: ProjectStore](
    mosaicImplicits: MosaicImplicits[HistStore],
    projStore: ProjStore) {
  import mosaicImplicits._

  def asMaml(ast: MapAlgebraAST)
    : (Expression, Option[NodeMetadata], Map[String, BacksplashMosaic]) = {

    def evalParams(ast: MapAlgebraAST): Map[String, BacksplashMosaic] = {
      val args = ast.args.map(evalParams)

      ast match {
        case MapAlgebraAST.ProjectRaster(_, projId, band, celltype, _) => {
          val bandActual = band.getOrElse(
            throw SingleBandOptionsException(
              "Band must be provided to evaluate AST"))
          // This is silly - mostly making up single band options here when all we really need is the band number
          Map[String, BacksplashMosaic](
            s"${projId.toString}_${bandActual}" -> (
              projStore
                .read(
                  projId,
                  None,
                  None,
                  None
                ) map { backsplashIm =>
                backsplashIm.copy(subsetBands = List(bandActual))
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
