package com.azavea.rf.batch.aoi

import com.azavea.rf.batch.Job
import java.sql.Timestamp
import java.time.ZonedDateTime

import cats.effect.IO
import cats.syntax.option._
import doobie.{ConnectionIO, Fragment, Fragments}
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import doobie.util.transactor.Transactor

import com.azavea.rf.database.Implicits._
import com.azavea.rf.database.util.RFTransactor

import java.util.UUID

case class FindAOIProjects(implicit val xa: Transactor[IO]) extends Job {
  val name = FindAOIProjects.name

  def run: Unit = {
    def timeToEpoch(timeFunc: String): Fragment = Fragment.const(s"extract(epoch from ${timeFunc})")
    val aoiProjectsToUpdate: ConnectionIO[List[UUID]] = {


      // get ids only
      val baseSelect: Fragment =
        fr"""
        select proj_table.id from (
          (projects proj_table inner join aois_to_projects on proj_table.id = aois_to_projects.project_id)
          inner join aois on aoi_id = aois.id
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

      (baseSelect ++ Fragments.whereAndOpt(isAoi, nowGtLastCheckedPlusCadence, aoiActive))
        .query[UUID]
        .to[List]
    }

    // TODO: this stdout-based process communication _extremely_ brittle. See #3263
    aoiProjectsToUpdate.transact(xa).unsafeRunSync.foreach(
      (projectId: UUID) => println(s"Project to update: ${projectId}")
    )
  }
}

object FindAOIProjects {
  val name = "find_aoi_projects"

  def main(args: Array[String]): Unit = {
    implicit val xa = RFTransactor.xa
    val job = FindAOIProjects()
    job.run
  }
}
