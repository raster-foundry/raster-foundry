package com.azavea.rf.batch.cogMetadata

import com.azavea.rf.common.RollbarNotifier
import com.azavea.rf.common.utils.CogUtils
import com.azavea.rf.datamodel.{LayerAttribute, Scene, SceneType}
import com.azavea.rf.database.util.RFTransactor
import com.azavea.rf.database.Implicits._
import com.azavea.rf.database.LayerAttributeDao

import cats.data._
import cats.effect.IO
import cats.implicits._
import doobie._
import doobie.implicits._

import geotrellis.raster.histogram.Histogram
import geotrellis.raster.io.json.HistogramJsonFormats

import io.circe.parser._

import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.concurrent.ExecutionContext.Implicits.global

import java.util.UUID

object HistogramBackfill extends RollbarNotifier with HistogramJsonFormats {

  implicit val xa = RFTransactor.xa

  type CogTuple = (UUID, Option[String])

  val name = "cog-histogram-backfill"

  def getScenesToBackfill: IO[List[List[CogTuple]]] = {
    logger.info("Finding COG scenes without histograms in layer_attributes")
    fr"""select
           id, ingest_location
         from
           scenes
         where
           scene_type = 'COG' and
           ingest_location is not null and
           cast(id as varchar(255)) not in (select layer_name from layer_attributes);
       """.query[CogTuple].to[List].transact(xa) map {
      tuples => {
        logger.info(s"Found ${tuples.length} scenes to create histograms for")
        tuples.grouped(8).toList
      }
    }
  }

  // presence of the ingest location is guaranteed by the filter in the sql string
  @SuppressWarnings(Array("OptionGet"))
  def insertHistogramLayerAttribute(cogTuple: CogTuple): OptionT[IO, LayerAttribute] = (
    for {
      histogram <- getSceneHistogram(cogTuple._2.get)
      inserted  <- OptionT(
        parse(histogram.toJson.toString).toOption traverse {
          parsed => LayerAttributeDao.insertLayerAttribute(
            LayerAttribute(
              cogTuple._1.toString, 0, "histogram", parsed
            )
          ).transact(xa)
        }
      )
    } yield inserted
  )

  def getSceneHistogram(ingestLocation: String): OptionT[IO, List[Histogram[Double]]] = {
    logger.info(s"Fetching histogram for scene at $ingestLocation")
    OptionT(
      IO.fromFuture {
        IO(
          (CogUtils.fromUri(ingestLocation) map {
             CogUtils.geoTiffDoubleHistogram(_).toList
           }).value
        )
      }
    )
  }

  def run: IO[Unit] = for {
    chunkedTuples <- getScenesToBackfill
    inserts       <- (chunkedTuples parTraverse { sceneList =>
      sceneList traverse { scene => insertHistogramLayerAttribute(scene) }
    }).value
  } yield {
    val allResults = inserts reduce { _ ++ _ }
    val errors = allResults filter { _.isEmpty }
    val successes = allResults filter { !_.isEmpty }
    logger.info(
      s"Created histograms for ${successes.length} scenes"
    )
    logger.warn(
      s"Failed to create histograms for ${errors.length} scenes"
    )
  }

  def main(args: Array[String]): Unit =
    run.recoverWith(
      {
        case t: Throwable =>
          IO {
            sendError(t)
            logger.error(t.getMessage, t)
          }
      }
    ).unsafeRunSync
}

