package com.rasterfoundry.batch.aoi

import com.rasterfoundry.batch.Job
import com.rasterfoundry.batch.util.conf.Config
import com.rasterfoundry.common.{AWSBatch, RollbarNotifier}
import com.rasterfoundry.database.util.RFTransactor

import cats.effect.IO
import cats.syntax.option._
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.transactor.Transactor
import doobie.{ConnectionIO, Fragment, Fragments}

import java.util.UUID

final case class FindAOIProjects(implicit val xa: Transactor[IO])
    extends Config
    with RollbarNotifier
    with AWSBatch {
  val name = FindAOIProjects.name

  def run(): Unit = {
    def timeToEpoch(timeFunc: String): Fragment =
      Fragment.const(s"extract(epoch from ${timeFunc})")

    val aoiProjectsToUpdate: ConnectionIO[List[UUID]] = {

      // get ids only
      val baseSelect: Fragment =
        fr"""
        select proj_table.id from (
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
        .query[UUID]
        .to[List]
    }

    val projectIds = aoiProjectsToUpdate.transact(xa).unsafeRunSync
    logger.info(s"Found the following projects to update: ${projectIds}")
    projectIds.map(kickoffAOIUpdateProject)
  }
}

object FindAOIProjects extends Job {
  val name = "find_aoi_projects"

  def runJob(args: List[String]): IO[Unit] = {
    if (args.length > 0) {
      logger.warn(s"Ignoring arguments to FindAOIProjects: ${args}")
    }
    RFTransactor.xaResource
      .use(xa => {
        implicit val transactor = xa
        val job = FindAOIProjects()
        IO { job.run }
      })
  }
}
