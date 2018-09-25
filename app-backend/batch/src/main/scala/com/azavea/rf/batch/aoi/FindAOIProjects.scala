package com.azavea.rf.batch.aoi

import java.util.UUID

import cats.effect.IO
import cats.syntax.option._
import com.azavea.rf.batch.Job
import com.azavea.rf.common.AWSBatch
import com.azavea.rf.database.util.RFTransactor
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
