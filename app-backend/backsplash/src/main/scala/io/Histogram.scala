package com.azavea.rf.backsplash.io

import com.azavea.rf.database.SceneToProjectDao
import com.azavea.rf.database.util.RFTransactor
import com.azavea.rf.datamodel.SceneType

import cats.data.OptionT
import cats.effect.IO
import cats.implicits._
import doobie.implicits._
import geotrellis.raster.MultibandTile
import geotrellis.raster.histogram._
import geotrellis.vector.{Projected, Polygon}

import java.util.UUID

object Histogram {

  implicit val xa = RFTransactor.xa

  def getRGBProjectHistogram(
      projectId: UUID,
      polygonOption: Option[Projected[Polygon]],
      redBand: Option[Int] = None,
      greenBand: Option[Int] = None,
      blueBand: Option[Int] = None,
      scenesSubset: List[UUID] = List.empty): IO[Vector[Histogram[Int]]] =
    for {
      mosaicDefinitions <- SceneToProjectDao
        .getMosaicDefinition(projectId,
                             polygonOption,
                             redBand,
                             greenBand,
                             blueBand,
                             scenesSubset)
        .transact(xa) map { _.toList } // needs to be a list for cats instances
      rgbs = (redBand, greenBand, blueBand).tupled match {
        case Some((red, green, blue)) =>
          List.fill(mosaicDefinitions.length)((red, green, blue))
        case _ =>
          mosaicDefinitions map { md =>
            (md.colorCorrections.redBand,
             md.colorCorrections.greenBand,
             md.colorCorrections.blueBand)
          }
      }
      globalTiles <- (mosaicDefinitions zip rgbs) parTraverse {
        case (mosaicDefinition, rgb) =>
          if (mosaicDefinition.sceneType == Some(SceneType.COG))
            Cog.fetchGlobalTile(mosaicDefinition,
                                polygonOption,
                                rgb._1,
                                rgb._2,
                                rgb._3)
          else
            // TODO: how?
            Avro.fetchGlobalTile(mosaicDefinition,
                                 polygonOption,
                                 rgb._1,
                                 rgb._2,
                                 rgb._3)
      }
      correctedTiles: List[MultibandTile] = mosaicDefinitions.zip(globalTiles) map {
        case (md, globalMbTile) =>
          val rgbBands = (redBand, greenBand, blueBand).tupled getOrElse {
            (md.colorCorrections.redBand,
             md.colorCorrections.greenBand,
             md.colorCorrections.blueBand)
          }
          val subsetTile =
            globalMbTile.subsetBands(rgbBands._1, rgbBands._2, rgbBands._3)
          md.colorCorrections.colorCorrect(subsetTile,
                                           subsetTile.histogramDouble)
      }
    } yield {
      correctedTiles.foldLeft(Vector.fill(3)(IntHistogram(): Histogram[Int]))(
        (hists: Vector[Histogram[Int]], mbTile: MultibandTile) => {
          (hists zip mbTile.histogram) map {
            case (h1, h2) => h1 merge h2
          }
        })
    }
}
