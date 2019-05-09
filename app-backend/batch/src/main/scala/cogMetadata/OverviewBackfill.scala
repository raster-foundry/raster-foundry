package com.rasterfoundry.batch.cogMetadata

import com.rasterfoundry.batch.Job
import com.rasterfoundry.common.RollbarNotifier
import com.rasterfoundry.datamodel.ProjectLayer
import com.rasterfoundry.database.ProjectLayerDao

import cats.effect.IO
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import fs2.Stream

object OverviewBackfill extends Job with RollbarNotifier {
  val name = "backfill-layer-overviews"
  val xa: Transactor[IO] = ???

  def getProjectLayerSceneCount(
      projectLayer: ProjectLayer
  ): Stream[ConnectionIO, (ProjectLayer, Int)] =
    fr"select count(1) from scenes_to_layers where project_layer_id = ${projectLayer.id}"
      .query[Int]
      .stream map { (projectLayer, _) }

  def kickoffOverviewGeneration(projectLayer: ProjectLayer): IO[Unit] = ???

  // Giant number inside listQ is because listQ needs a limit parameter, but we don't actually
  // want to limit
  val projectLayers: Stream[ConnectionIO, ProjectLayer] =
    ProjectLayerDao.query.listQ(1000000).stream
  val projectLayersWithSceneCounts: Stream[ConnectionIO, (ProjectLayer, Int)] =
    projectLayers.flatMap(getProjectLayerSceneCount)

  def runJob(args: List[String]): IO[Unit] =
    projectLayersWithSceneCounts
      .filter({
        case (_, n) => n <= 300
      })
      .transact(xa)
      .parEvalMap(20)({
        case (projectLayer, _) =>
          kickoffOverviewGeneration(projectLayer)
      })
      .fold(())((_: Unit, _: Unit) => ())
      .compile
      .to[List]
      .map { _.head }
}
