package com.rasterfoundry.batch.cogMetadata

import com.rasterfoundry.batch.Job
import com.rasterfoundry.common.RollbarNotifier
import com.rasterfoundry.datamodel.ProjectLayer

import cats.effect.IO
import doobie.{ConnectionIO, Transactor}
import doobie.implicits._
import fs2.Stream

object OverviewBackfill extends Job with RollbarNotifier {
  val name = "backfill-layer-overviews"
  val xa: Transactor[IO] = ???

  def getProjectLayerSceneCount(projectLayer: ProjectLayer): Stream[ConnectionIO, (ProjectLayer, Int)] =
    ???

  def kickoffOverviewGeneration(projectLayer: ProjectLayer): IO[Unit] = ???

  val projectLayers: Stream[ConnectionIO, ProjectLayer] = ???
  val projectLayersWithSceneCounts: Stream[ConnectionIO, (ProjectLayer, Int)] =
    projectLayers.flatMap(getProjectLayerSceneCount)

  def runJob(args: List[String]): IO[Unit] =
    projectLayersWithSceneCounts
      .filter({
        case (_, n) => n <= 300
      })
      .transact(xa)
      .parEvalMap(15)({
        case (projectLayer, _) =>
          kickoffOverviewGeneration(projectLayer)
      })
      .fold(())((_: Unit, _: Unit) => ())
      .compile
      .to[List]
      .map { _.head }
}
