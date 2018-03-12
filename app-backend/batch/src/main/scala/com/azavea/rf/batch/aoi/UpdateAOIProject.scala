package com.azavea.rf.batch.aoi

import com.auth0.client.mgmt._
import com.auth0.client.mgmt.filter.UserFilter
import com.auth0.client.auth._
import com.auth0.json.auth.TokenHolder
import com.azavea.rf.batch.Job
import com.azavea.rf.datamodel._
import com.azavea.rf.database.meta.RFMeta._
import cats.data._
import java.sql.Timestamp
import java.time.ZonedDateTime
import java.util.UUID

import cats.effect.IO
import doobie._
import doobie.implicits._
import doobie.util.transactor.Transactor
import io.circe._
import io.circe.syntax._

import scala.concurrent.Future
import scala.util.{Failure, Success}

import geotrellis.slick.Projected
import geotrellis.vector._

case class UpdateAOIProject(projectId: UUID)(implicit val xa: Transactor[IO]) extends Job {
  val name = FindAOIProjects.name


  def run: Unit = {
    // get project, AOIToProject, AOI query params, and owner User obj from project ID
    // (should be doable in one go -- either with connection IO sequenced or with one big
    // query and getting it to grab everything from a join to the project ID and tossing on
    // case classes into .query[(x, y, z, w)] until it exhausts the columns)
    // (provided it's just types that have to line up and that doobie doesn't care about names)

    // it's not obvious whether a project can have more than one AOI, so assume that a list might
    // come back and deal with everything that does

    // things I definitely need:
    // filters from AOI
    // project owner
    // 

    // TODO: this shouldn't be necessary for it to find uuidMeta, since that's in RFMeta
    implicit val uuidMeta: Meta[UUID] = {
      Meta.other[String]("text").xmap[UUID](
        a => UUID.fromString(a),
        a => a.toString
      )
    }
    implicit val uuidComposite = Composite[UUID]

    def justGetProject: ConnectionIO[List[Project]] = {
      sql"""select * from projects"""
        .query[Project]
        .stream
        .compile.toList
    }

    def fetchBaseData: ConnectionIO[List[(Project, CombinedSceneQueryParams)]] = {
      val base = sql"""
      SELECT
        p.id, p.created_at, p.modified_at, p.organization_id, p.created_by,
        p.modified_by, p.owner, p.name, p.slug_label, p.description,
        p.visibility, p.tile_visibility, p.is_aoi_project,
        p.aoi_cadence_millis, p.aois_last_checked, p.tags, p.extent,
        p.manual_order, p.is_single_band, p.single_band_options, aois.filters
      FROM
        (projects p inner join aois_to_projects atp
        on
          p.id = atp.project_id) patp
        inner join aois a on
          patp.aoi_id = aois.id
      WHERE
      """
      val projectIdFilter: Option[Fragment] = Some(Fragment.const("id = ${projectId}"))

      (base ++ Fragments.whereAndOpt(projectIdFilter))
        .query[(Project, Json)]
        .stream
        .map({ case (p: Project, f:Json) => (p, f.as[CombinedSceneQueryParams]) })
        .compile.toList
    }

    def getScenesForProject(projectId: UUID, queryParams: CombinedSceneQueryParams, area: Projected[MultiPolygon]): ConnectionIO[List[Scene]] = ???

    // filter new scenes to the aoi area and query params
//    val authApi = new AuthAPI(auth0Config.domain, auth0Config.clientId, auth0Config.clientSecret)
//    val mgmtTokenRequest = authApi.requestToken(s"https://${auth0Config.domain}/api/v2/")
//    val mgmtToken = mgmtTokenRequest.execute
//    val mgmtApi = new ManagementAPI(auth0Config.domain, mgmtToken.getAccessToken)
//
//    logger.info(s"Updating Project $projectId AOI...")
//    val result = for {
//      user: User                   <- OptionT(Users.getUserById(systemUser))
//      (project: Project, aoi: AOI) <- OptionT(Projects.getAOIProject(projectId, user))
//      scenes: Iterable[UUID]       <- OptionT(
//        {
//          val area = aoi.area
//          val queryParams =
//            aoi
//              .filters.as[CombinedSceneQueryParams]
//              .getOrElse(throw new Exception(s"No valid queryParams passed: ${aoi.filters}"))
//
//          val scenes: Future[Iterable[UUID]] = (
//            for {
//              sceneIds <- OptionT.liftF(
//                database.db.run {
//                  Scenes
//                    .filterScenes(queryParams)
//                    .filterUserVisibility(user)
//                    .filter { s => s.dataFootprint.intersects(area) && s.createdAt > project.aoisLastChecked }
//                    .map { _.id }
//                    .result
//                }
//              )
//              projects <- OptionT.liftF(Projects.addScenesToProject(sceneIds, projectId, user))
//            } yield projects.map(_.id)).value.map(_.getOrElse(Seq()))
//
//
//          scenes.map {
//            scenesSeq => {
//              logger.info(s"Project $projectId is updated.")
//              Projects.updateProject(
//                project.copy(aoisLastChecked = Timestamp.from(ZonedDateTime.now.toInstant)),
//                projectId,
//                user
//              )
//              scenesSeq.some
//            }
//          }
//        }
//      )
//    } yield (scenes, project)
//
//    val future: Future[Iterable[UUID]] = result.value.map {
//      (option: Option[(Iterable[UUID], Project)]) =>
//      option match {
//        case Some((scenes: Iterable[UUID], project: Project)) =>
//          val user = mgmtApi.users().get(project.owner, new UserFilter()).execute()
//          logger.info(s"Notifications stub - ${project.owner} -> ${user.getEmail}")
//          scenes
//        case _ =>
//          Seq()
//      }
//    }
//
//    future onComplete {
//      case Success(seq) => {
//        if(seq.nonEmpty) logger.info(s"Updated Project $projectId with scenes: ${seq.mkString(",")}")
//        else logger.info(s"Project $projectId required no update")
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

object UpdateAOIProject {
  val name = "update_aoi_project"

  def main(args: Array[String]): Unit = {
//    implicit val db = DB.DEFAULT
//
//    val job = args.toList match {
//      case List(projectId) => UpdateAOIProject(UUID.fromString(projectId))
//      case _ =>
//        throw new IllegalArgumentException("Argument could not be parsed to UUID")
//    }
//
//    job.run
  }
}
