package com.azavea.rf.batch.aoi

import com.azavea.rf.batch.Job
import com.azavea.rf.database.tables._
import com.azavea.rf.database.{Database => DB}
import java.sql.Timestamp
import java.time.ZonedDateTime

import cats.effect.IO
import doobie.util.transactor.Transactor

import scala.util.{Failure, Success}

case class FindAOIProjects(implicit val xa: Transactor[IO]) extends Job {
  val name = FindAOIProjects.name

//  import database.driver.api._
//
//  /** Convert Long to Timestamp function */
//  protected val toTimestamp = SimpleFunction.unary[Long, Timestamp]("to_timestamp")
//
  def run: Unit = {
//    logger.info("Finding AOI projects...")
//    Users.getUserById(systemUser).flatMap { userOpt =>
//      val user = userOpt.getOrElse {
//        val e = new Exception(s"User $systemUser doesn't exist.")
//        sendError(e)
//        throw e
//      }
//
//      database.db.run {
//        Projects
//          .filterToSharedOrganizationIfNotInRoot(user)
//          .filter { p =>
//            p.isAOIProject && p.aoisLastChecked <= toTimestamp(p.aoiCadenceMillis * (-1l) + ZonedDateTime.now.toInstant.toEpochMilli)
//          }
//          .join(AoisToProjects)
//          .on { case (p, o) => p.id === o.projectId }
//          .join(AOIs)
//          .on { case ((_, atp), a) => atp.aoiId === a.id }
//          .map { case ((p, _), _) => p.id }
//          .result
//      }
//    } onComplete {
//      case Success(seq) => {
//        println(s"ProjectIds: ${seq.map(_.toString).mkString(",")}")
//        stop
//      }
//      case Failure(e) => {
//        e.printStackTrace()
//        sendError(e)
//        stop
//        sys.exit(1)
//      }
//    }
  }
}

object FindAOIProjects {
  val name = "find_aoi_projects"

  def main(args: Array[String]): Unit = {
//    implicit val db = DB.DEFAULT
//
//    val job = args.toList match {
//      case _ => FindAOIProjects()
//    }
//
//    job.run
  }
}
