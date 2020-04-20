package com.rasterfoundry.batch.cogMetadata

import com.rasterfoundry.batch.Job
import com.rasterfoundry.common.LayerAttribute
import com.rasterfoundry.common.RollbarNotifier
import com.rasterfoundry.common.utils.CogUtils
import com.rasterfoundry.database.util.RFTransactor
import com.rasterfoundry.database.{LayerAttributeDao, SceneDao}

import cats.effect.IO
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import geotrellis.raster.histogram.Histogram
import geotrellis.raster.io.json.HistogramJsonFormats
import io.circe.syntax._

import java.util.UUID

object HistogramBackfill
    extends Job
    with RollbarNotifier
    with HistogramJsonFormats {

  type CogTuple = (UUID, Option[String])

  val name = "cog-histogram-backfill"

  def getScenesToBackfill(
      implicit xa: Transactor[IO]
  ): IO[List[List[CogTuple]]] = {
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
  def insertHistogramLayerAttribute(cogTuple: CogTuple)(
      implicit xa: Transactor[IO]
  ): IO[Option[LayerAttribute]] = {
    val histogram = getSceneHistogram(cogTuple._2.get)
    LayerAttributeDao
      .insertLayerAttribute(
        LayerAttribute(
          cogTuple._1.toString,
          0,
          "histogram",
          histogram.asJson
        )
      )
      .map({ layerAttribute: LayerAttribute =>
        Some(layerAttribute)
      })
      .transact(xa)
  }

  def getSceneHistogram(
      ingestLocation: String
  ): Option[Array[Histogram[Double]]] = {
    logger.info(s"Fetching histogram for scene at $ingestLocation")
    val histO = CogUtils.histogramFromUri(ingestLocation)
    if (histO.isEmpty) {
      logger.info(s"Fetching histogram for scene at $ingestLocation failed")
    }
    histO
  }

  def runJob(args: List[String]): IO[Unit] = {
    implicit val xa =
      RFTransactor.nonHikariTransactor(RFTransactor.TransactorConfig())
    for {
      sceneTupleChunks <- args map { UUID.fromString(_) } match {
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
      allResults = ios flatMap {
        _.recoverWith({
          case t: Throwable =>
            sendError(t)
            logger.error(t.getMessage, t)
            IO(List.empty[Option[LayerAttribute]])
        }).unsafeRunSync
      }
      errors = allResults filter { _.isEmpty }
      successes = allResults filter { !_.isEmpty }
    } yield {
      logger.info(
        s"""
              | Created histograms for ${successes.length} scenes.
              | Failed to create histograms for ${errors.length} scenes.
              """.trim.stripMargin
      )
    }
  }
}
