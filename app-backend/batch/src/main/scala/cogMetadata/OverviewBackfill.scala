package com.rasterfoundry.batch.cogMetadata

import com.rasterfoundry.batch.Job
import com.rasterfoundry.common.{AWSLambda, RollbarNotifier}
import com.rasterfoundry.datamodel.ProjectLayer
import com.rasterfoundry.database.ProjectLayerDao
import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.database.util.RFTransactor.xaResource

import cats.effect.IO
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import fs2.Stream

object OverviewBackfill extends Job with RollbarNotifier {
  val name = "backfill-layer-overviews"

  def getProjectLayerSceneCount(
      projectLayer: ProjectLayer
  ): Stream[ConnectionIO, (ProjectLayer, Int)] =
    fr"select count(1) from scenes_to_layers where project_layer_id = ${projectLayer.id}"
      .query[Int]
      .stream map { (projectLayer, _) }

  def kickoffOverviewGeneration(
      projectLayer: ProjectLayer
  ): IO[Unit] = IO {
    logger.info(
      s"Kicking off lambda overview generation for layer ${projectLayer.id}")
    if (AWSLambda.runLambda) {
      AWSLambda
        .kickoffLayerOverviewCreate(
          projectLayer.projectId.get,
          projectLayer.id,
          "RequestResponse"
        )
    } else {
      logger.info("Sleeping for 10 seconds to pretend to do work")
      Thread.sleep(10000)
    }
  }

  // Giant number inside listQ is because listQ needs a limit parameter, but we don't actually
  // want to limit
  val projectLayers: Stream[ConnectionIO, ProjectLayer] =
    ProjectLayerDao.query
      .filter(fr"project_id IS NOT NULL")
      .listQ(1000000)
      .stream
  val projectLayersWithSceneCounts: Stream[ConnectionIO, (ProjectLayer, Int)] =
    projectLayers.flatMap(getProjectLayerSceneCount)

  def runJob(args: List[String]): IO[Unit] = {
    xaResource
      .use(
        t =>
          projectLayersWithSceneCounts
            .filter({
              case (_, n) => n <= 300 && n > 0
            })
            .transact(t)
            .parEvalMap(20)({
              case (projectLayer, _) =>
                kickoffOverviewGeneration(projectLayer)
            })
            .compile
            .to[List]
      )
      .map { results =>
        logger.info(s"Backfilled overviews for ${results.length} project layers")
      }
  }
}
