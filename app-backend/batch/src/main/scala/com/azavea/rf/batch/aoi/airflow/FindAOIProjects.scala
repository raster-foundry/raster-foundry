package com.azavea.rf.batch.aoi.airflow

import com.azavea.rf.batch.Job
import com.azavea.rf.database.tables._
import com.azavea.rf.database.{Database => DB}

import java.sql.Timestamp
import java.time.ZonedDateTime

import scala.util.{Failure, Success}

case class FindAOIProjects(implicit val database: DB) extends Job {
  val name = FindAOIProjects.name

  import database.driver.api._

  /** Function to sum Reps of diff types */
  val sumTime: (Rep[String], Rep[Timestamp], Rep[Long]) => Rep[Timestamp] =
    SimpleFunction.ternary[String, Timestamp, Long, Timestamp]("+")

  def run: Unit = {
    logger.info("Finding AOI projects...")
    Users.getUserById(airflowUser).flatMap { userOpt =>
      val user = userOpt.getOrElse {
        val e = new Exception(s"User $airflowUser doesn't exist.")
        sendError(e)
        stop
        throw e
      }

      database.db.run {
        Projects
          .filterToSharedOrganizationIfNotInRoot(user)
          .filter { p =>
            p.isAOIProject && sumTime("TIME", p.aoisLastChecked, p.aoiCadenceMillis) <= Timestamp.from(ZonedDateTime.now.toInstant)
          }
          .join(AoisToProjects)
          .on { case (p, o) => p.id === o.projectId }
          .join(AOIs)
          .on { case ((_, atp), a) => atp.aoiId === a.id }
          .map { case ((p, _), _) => p.id }
          .result
      }
    } onComplete {
      case Success(seq) => {
        println(s"ProjectIds: ${seq.map(_.toString).mkString(",")}")
        stop
      }
      case Failure(e) => {
        sendError(e)
        stop
        throw e
      }
    }
  }
}

object FindAOIProjects {
  val name = "find_aoi_projects"

  def main(args: Array[String]): Unit = {
    implicit val db = DB.DEFAULT

    val job = args.toList match {
      case _ => FindAOIProjects()
    }

    job.run
  }
}
