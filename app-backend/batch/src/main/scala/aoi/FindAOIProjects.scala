package com.rasterfoundry.batch.aoi

import java.util.UUID

import cats.effect.IO
import cats.syntax.option._
import com.rasterfoundry.batch.Job
import com.rasterfoundry.common.AWSBatch
import com.rasterfoundry.database.util.RFTransactor
import com.typesafe.scalalogging.LazyLogging
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.transactor.Transactor
import doobie.{ConnectionIO, Fragment, Fragments}

final case class FindAOIProjects(implicit val xa: Transactor[IO])
    extends Job
    with AWSBatch {
  val name = FindAOIProjects.name

  def run(): Unit = {
    def timeToEpoch(timeFunc: String): Fragment =
      Fragment.const(s"extract(epoch from ${timeFunc})")

    val aoiProjectsToUpdate: ConnectionIO[List[(UUID, Long)]] = {

      // get ids only
      val baseSelect: Fragment =
        fr"""
        select (proj_table.id, proj_table.aoi_cadence_millis / 1000 from (
          (projects proj_table inner join aois on proj_table.id = aois.project_id)
        )"""

      //  check to make sure the project is an aoi project
      val isAoi: Option[Fragment] = fr"is_aoi_project=true".some

      val aoiActive: Option[Fragment] = fr"aois.is_active=true".some

      // Check to make sure now is later than last checked + cadence
      val nowGtLastCheckedPlusCadence: Option[Fragment] = {
        timeToEpoch("now()") ++
          fr" > " ++
          timeToEpoch("aois_last_checked") ++
          fr"+ aoi_cadence_millis / 1000"
      }.some

      (baseSelect ++ Fragments.whereAndOpt(isAoi,
                                           nowGtLastCheckedPlusCadence,
                                           aoiActive))
        .query[(UUID, Long)]
        .to[List]
    }

    val projectIdsAndCadences = aoiProjectsToUpdate.transact(xa).unsafeRunSync
    logger.info(s"Found the following projects to update: ${projectIdsAndCadences map { _._1 }}")
    projectIdsAndCadences map {
      // if the cadence is at least as frequent as one day
      // we probably could use a heuristic for AOI area also but I don't know what that should be
      case (projectId, cadence) if cadence <= 86400 =>
        kickoffAOIUpdateProject(projectId)
      // if it's less frequent than a day, bump memory to 15000, since there will probably be a _lot_
      // of scenes
      case (projectId, _) =>
        kickoffAOIUpdateProject(projectId, 15000)
    }
  }
}

object FindAOIProjects extends LazyLogging {
  val name = "find_aoi_projects"

  def main(args: Array[String]): Unit = {
    if (args.length > 0) {
      logger.warn(s"Ignoring arguments to FindAOIProjects: ${args}")
    }
    implicit val xa = RFTransactor.xa
    val job = FindAOIProjects()
    job.run
  }
}
