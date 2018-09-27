package com.azavea.rf.batch.cogMetadata

import com.azavea.rf.common.RollbarNotifier
import com.azavea.rf.common.utils.CogUtils
import com.azavea.rf.datamodel.{LayerAttribute, Scene, SceneType}
import com.azavea.rf.database.util.RFTransactor
import com.azavea.rf.database.Implicits._
import com.azavea.rf.database.{LayerAttributeDao, SceneDao}

import cats.data._
import cats.effect.IO
import cats.implicits._
import doobie._
import doobie.implicits._

import geotrellis.raster.histogram.Histogram
import geotrellis.raster.io.json.HistogramJsonFormats

import io.circe.parser._
import io.circe.syntax._

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
           scenes left join layer_attributes on
             cast(scenes.id as varchar(255)) = layer_attributes.layer_name
         where
           scene_type = 'COG' and
           ingest_location is not null and
           layer_attributes.layer_name is null;
       """.query[CogTuple].to[List].transact(xa) map { tuples =>
      {
        logger.info(s"Found ${tuples.length} scenes to create histograms for")
        tuples.grouped((tuples.length / 10) max 1).toList
      }
    }
  }

  // presence of the ingest location is guaranteed by the filter in the sql string
  @SuppressWarnings(Array("OptionGet"))
  def insertHistogramLayerAttribute(
      cogTuple: CogTuple): IO[Option[LayerAttribute]] = (
    for {
      histogram <- getSceneHistogram(cogTuple._2.get)
      inserted <- parse(histogram.toJson.toString).toOption match {
        case Some(x) if x.toString == "null" | None =>
          logger.info("Nah my dude none parsed")
          None.pure[IO]
        case Some(parsed) =>
          logger.info("Yeah my dude some parsed")
          logger.info(s"Parsed: $parsed")
          LayerAttributeDao
            .insertLayerAttribute(
              LayerAttribute(
                cogTuple._1.toString,
                0,
                "histogram",
                parsed
              )
            )
            .map({ layerAttribute: LayerAttribute =>
              Some(layerAttribute)
            })
            .transact(xa)
      }
    } yield inserted
  )

  def getSceneHistogram(
      ingestLocation: String): IO[Option[List[Histogram[Double]]]] = {
    logger.info(s"Fetching histogram for scene at $ingestLocation")
    IO.fromFuture {
      IO(
        (CogUtils.fromUri(ingestLocation) map {
          CogUtils.geoTiffDoubleHistogram(_).toList
        }).value
      )
    } recoverWith ({
      case t: Throwable =>
        sendError(t)
        logger.info(s"Fetching histogram for scene at $ingestLocation failed")
        logger.error(t.getMessage, t)
        Option.empty[List[Histogram[Double]]].pure[IO]
    })
  }

  def run(sceneIds: Array[UUID]): IO[Unit] =
    for {
      sceneTupleChunks <- sceneIds.toList match {
        // If we don't have any ids, just get all the COG scenes without histograms
        case Nil => getScenesToBackfill
        // If we do have some ids, go get those scenes. Assume the user has picked scenes correctly
        case ids =>
          ids traverse { id =>
            {
              SceneDao.unsafeGetSceneById(id).transact(xa) map { scene =>
                (scene.id, scene.ingestLocation)
              }
            }
          } map { List(_) }
      }
      ios <- IO {
        sceneTupleChunks map { chunk =>
          chunk traverse { idWithIngestLoc =>
            insertHistogramLayerAttribute(idWithIngestLoc)
          }
        }
      }
    } yield {
      val allResults: List[Option[LayerAttribute]] = ios flatMap {
        _.recoverWith({
          case t: Throwable =>
            sendError(t)
            logger.error(t.getMessage, t)
            IO(List.empty[Option[LayerAttribute]])
        }).unsafeRunSync
      }
      val errors = allResults filter { _.isEmpty }
      val successes = allResults filter { !_.isEmpty }
      logger.info(
        s"Created histograms for ${successes.length} scenes"
      )
      logger.warn(
        s"Failed to create histograms for ${errors.length} scenes"
      )
      sys.exit(0)
    }

  def main(args: Array[String]): Unit =
    run(args map { UUID.fromString(_) }).unsafeRunSync
}
