package com.rasterfoundry.backsplash.io

import com.rasterfoundry.database.SceneToProjectDao
import com.rasterfoundry.database.util.RFTransactor
import com.rasterfoundry.datamodel.SceneType

import cats.data.OptionT
import cats.effect._
import cats.implicits._
import doobie.implicits._
import fs2.Stream
import geotrellis.raster.MultibandTile
import geotrellis.raster.histogram._
import geotrellis.vector.{Projected, Polygon}

import java.util.UUID

object Histogram {

  implicit val xa = RFTransactor.xa

  def getRGBProjectHistogram(projectId: UUID,
                             polygonOption: Option[Projected[Polygon]],
                             redBand: Option[Int] = None,
                             greenBand: Option[Int] = None,
                             blueBand: Option[Int] = None,
                             scenesSubset: List[UUID] = List.empty)(
      implicit cs: ContextShift[IO]): IO[Vector[Histogram[Int]]] =
    (for {
      mosaicDefinition <- SceneToProjectDao
        .getMosaicDefinition(projectId,
                             polygonOption,
                             redBand,
                             greenBand,
                             blueBand,
                             scenesSubset)
        .transact(xa)
      rgb = (redBand, greenBand, blueBand).tupled match {
        case Some((red, green, blue)) =>
          (red, green, blue)
        case _ =>
          (mosaicDefinition.colorCorrections.redBand,
           mosaicDefinition.colorCorrections.greenBand,
           mosaicDefinition.colorCorrections.blueBand)
      }
      globalTile <- Stream.eval {
        if (mosaicDefinition.sceneType == Some(SceneType.COG))
          Cog.fetchGlobalTile(mosaicDefinition,
                              polygonOption,
                              rgb._1,
                              rgb._2,
                              rgb._3)
        else
          Avro.fetchGlobalTile(mosaicDefinition,
                               polygonOption,
                               rgb._1,
                               rgb._2,
                               rgb._3)
      }
      correctedTile = {
        val rgbBands = (redBand, greenBand, blueBand).tupled getOrElse {
          (mosaicDefinition.colorCorrections.redBand,
           mosaicDefinition.colorCorrections.greenBand,
           mosaicDefinition.colorCorrections.blueBand)
        }
        val subsetTile =
          globalTile.subsetBands(rgbBands._1, rgbBands._2, rgbBands._3)
        mosaicDefinition.colorCorrections.colorCorrect(
          subsetTile,
          subsetTile.histogramDouble,
          None)
      }
    } yield {
      correctedTile.histogram
    }).compile.fold(Vector.fill(3)(IntHistogram(): Histogram[Int]))(
      (hists1: Vector[Histogram[Int]], hists2: Array[Histogram[Int]]) => {
        (hists1 zip hists2) map {
          case (h1, h2) => h1 merge h2
        } toVector
      }
    )
}
