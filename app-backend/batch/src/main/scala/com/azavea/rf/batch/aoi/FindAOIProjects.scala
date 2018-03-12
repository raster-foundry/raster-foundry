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

import scala.util.{Failure, Success}
import java.util.UUID

case class FindAOIProjects(implicit val xa: Transactor[IO]) extends Job {
  val name = FindAOIProjects.name

  def run: Unit = {
    def timeToEpoch(timeFunc: String): Fragment = Fragment.const(s"extract(epoch from ${timeFunc})")
    val aoiProjectsToUpdate: ConnectionIO[List[UUID]] = {

      // get ids only
      val baseSelect: Fragment = sql"select id from projects"

      //  check to make sure the project is an aoi project
      val isAoi: Option[Fragment] = fr"is_aoi_project=true".some

      // Check to make sure now is later than last checked + cadence
      val nowGtLastCheckedPlusCadence: Option[Fragment] = {
        timeToEpoch("now()") ++
          fr" > " ++
          timeToEpoch("aois_last_checked") ++
          fr"+ aoi_cadence_millis / 1000"
      }.some

      (baseSelect ++ Fragments.whereAndOpt(isAoi, nowGtLastCheckedPlusCadence))
        .query[UUID]
        .stream
        .compile.toList
    }

    aoiProjectsToUpdate.transact(xa).unsafeRunSync.foreach(println)
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
