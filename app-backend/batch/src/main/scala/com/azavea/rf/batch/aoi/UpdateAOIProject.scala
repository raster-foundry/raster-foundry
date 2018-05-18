package com.azavea.rf.batch.aoi

import com.auth0.client.mgmt._
import com.auth0.client.mgmt.filter.UserFilter
import com.auth0.client.auth._
import com.auth0.json.auth.TokenHolder
import com.azavea.rf.batch.Job
import com.azavea.rf.datamodel._
import java.sql.Timestamp
import java.time.ZonedDateTime
import java.util.UUID

import cats._
import cats.data._
import cats.implicits._
import cats.syntax.option._
import cats.effect.IO
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import doobie.util.transactor.Transactor
import io.circe._
import io.circe.Decoder.Result
import io.circe.syntax._

import scala.concurrent.Future
import scala.util.{Failure, Success}

import geotrellis.slick.Projected
import geotrellis.vector._

import com.azavea.rf.common.AWSBatch
import com.azavea.rf.database.{ProjectDao, SceneWithRelatedDao, UserDao}
import com.azavea.rf.database.Implicits._
import com.azavea.rf.database.util.RFTransactor

import java.sql.Timestamp

case class UpdateAOIProject(projectId: UUID)(implicit val xa: Transactor[IO]) extends Job with AWSBatch {
  val name = FindAOIProjects.name

  type LastChecked = Timestamp
  type StartTime = Timestamp

  def run: Unit = {
    logger.info(s"Updating project ${projectId}")
    /** Fetch the project, AOI area, last checked time, start time, and AOI scene query parameters */
    def fetchBaseData: ConnectionIO[
      (String, Projected[MultiPolygon], StartTime, LastChecked, Result[CombinedSceneQueryParams])] = {
      val base = fr"""
      SELECT
        owner, area, start_time, aois_last_checked, filters
      FROM
        ((select id proj_table_id, owner, aois_last_checked from projects) projects_filt
        inner join aois_to_projects atp
        on
          projects_filt.proj_table_id = atp.project_id) patp
        inner join (select id aoi_id, area, filters from aois) aois_filt on
          patp.aoi_id = aois_filt.aoi_id
      """
      val projectIdFilter: Option[Fragment] = Some(fr"proj_table_id = ${projectId}")

      // This could return a list probably if we ever support > 1 AOI per project
      (base ++ Fragments.whereAndOpt(projectIdFilter))
        .query[(String, Projected[MultiPolygon], StartTime, LastChecked, Json)]
        .unique
        .map(
          { case (projOwner: String, g: Projected[MultiPolygon], startTime: StartTime, lastChecked: LastChecked, f:Json) => (projOwner, g, startTime, lastChecked, f.as[CombinedSceneQueryParams])
          }
        )
    }

    /** Find all the scenes that can be added to a project */
    def fetchProjectScenes(
      user: User, geom: Projected[MultiPolygon], startTime: StartTime,
      lastChecked: LastChecked, queryParams: Option[CombinedSceneQueryParams]): ConnectionIO[List[UUID]] = {
      val base: Fragment = fr"SELECT id FROM scenes"
      val qpFilters: Option[Fragment] = queryParams map {
        (csp: CombinedSceneQueryParams) => {
          val queryFilters = SceneWithRelatedDao.makeFilters(List(csp)).flatten
          Fragments.and(queryFilters.flatten.toSeq: _*)
        }
      }
      val alreadyCheckedFilter: Option[Fragment] = Some(fr"created_at > ${lastChecked}")
      val acquisitionFilter: Option[Fragment] = Some(fr"acquisition_date > ${startTime}")
      val areaFilter: Option[Fragment] = Some(fr"st_intersects(data_footprint, ${geom})")
      val ownerFilter: Option[Fragment] = if (user.isInRootOrganization) {
          None
        } else {
          Some(fr"(organization_id = ${user.organizationId} OR owner = ${user.id})")
        }
      (base ++ Fragments.whereAndOpt(qpFilters, alreadyCheckedFilter, acquisitionFilter, areaFilter, ownerFilter))
        .query[UUID]
        .stream
        .compile.toList
    }

    def addScenesToProjectWithProjectIO: ConnectionIO[UUID] = {
      val user: ConnectionIO[Option[User]] = fetchBaseData flatMap {
        case (projOwner: String, _, _, _, _) => UserDao.getUserById(projOwner)
      }
      val sceneIds: ConnectionIO[List[UUID]] =
        fetchBaseData flatMap {
          case (_, g: Projected[MultiPolygon], startTime: StartTime, lastChecked: LastChecked, qp: Result[CombinedSceneQueryParams]) =>
            user flatMap {
              case Some(u) => fetchProjectScenes(u, g, startTime, lastChecked, qp.toOption)
              case None =>
                throw new Exception(
                  "User not found. Should be impossible given foreign key relationship on project")
            }
        }
      sceneIds flatMap {
        case sceneIds: List[UUID] => {
          logger.info(s"Adding the following scenes to project ${projectId}: ${sceneIds}")
          user flatMap {
            case Some(u) => ProjectDao.addScenesToProject(sceneIds, projectId, u) map { _ => projectId }
            case None =>
              throw new Exception(
                "User not found. Should be impossible given foreign key relationship on project")
          }
        }
      }
    }

    def kickoffIngestsIO: ConnectionIO[Unit] = for {
      projId <- addScenesToProjectWithProjectIO
      sceneIds <- SceneWithRelatedDao.getScenesToIngest(projId) map {
        (scenes: List[Scene.WithRelated]) => scenes map { _.id }
      }
    } yield { sceneIds map { kickoffSceneIngest } }

    kickoffIngestsIO.transact(xa).unsafeRunSync
  }
}

object UpdateAOIProject {
  val name = "update_aoi_project"

  implicit val xa = RFTransactor.xa

  def main(args: Array[String]): Unit = {

    val job = args.toList match {
     case List(projectId) => UpdateAOIProject(UUID.fromString(projectId))
     case _ =>
       throw new IllegalArgumentException("Argument could not be parsed to UUID")
   }

   job.run
  }
}
