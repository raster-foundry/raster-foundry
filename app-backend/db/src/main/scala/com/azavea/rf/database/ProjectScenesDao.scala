package com.azavea.rf.database

import com.azavea.rf.database.Implicits._
import com.azavea.rf.database.util.Page
import com.azavea.rf.datamodel._
import com.azavea.rf.datamodel.{
  Scene,
  SceneFilterFields,
  SceneStatusFields,
  User,
  Visibility
}
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import cats._
import cats.data._
import cats.effect.IO
import cats.implicits._

import com.lonelyplanet.akka.http.extensions.{PageRequest, Order}

import java.util.UUID

object ProjectScenesDao extends Dao[Scene] {
  val tableName = "scenes_to_projects INNER JOIN scenes ON scene_id = id"
  val selectF = fr"""
      SELECT
      id, created_at, created_by, modified_at, modified_by, owner,
          visibility, tags,
          datasource, scene_metadata, name, tile_footprint,
          data_footprint, metadata_files, ingest_location, cloud_cover,
          acquisition_date, sun_azimuth, sun_elevation, thumbnail_status,
          boundary_status, ingest_status, scene_type FROM""" ++ tableF

  def listProjectScenes(projectId: UUID,
                        pageRequest: PageRequest,
                        sceneParams: CombinedSceneQueryParams)
    : ConnectionIO[PaginatedResponse[Scene.ProjectScene]] = {

    val projectQuery = ProjectDao.query.filter(projectId).select
    val andPendingF: Fragment = sceneParams.sceneParams.pending match {
      case Some(true) => fr"accepted = false"
      case _          => fr"accepted = true"
    }

    val manualOrderF = fr"ORDER BY scene_order ASC, id ASC"
    val autoOrderF = fr"ORDER BY acquisition_date ASC, cloud_cover ASC"
    val filterQ = query
      .filter(fr"project_id = ${projectId}")
      .filter(andPendingF)
      .filter(sceneParams)

    val paginatedScenes = for {
      project <- projectQuery
      page <- project.manualOrder match {
        case true => filterQ.page(pageRequest, manualOrderF)
        case _    => filterQ.page(pageRequest, autoOrderF)
      }
    } yield page
    paginatedScenes.flatMap { (pr: PaginatedResponse[Scene]) =>
      scenesToProjectScenes(pr.results.toList, projectId).map(
        projectScenes =>
          PaginatedResponse[Scene.ProjectScene](
            pr.count,
            pr.hasPrevious,
            pr.hasNext,
            pr.page,
            pr.pageSize,
            projectScenes
        )
      )
    }
  }

  // We know the datasources list head exists because of the foreign key relationship
  @SuppressWarnings(Array("TraversableHead"))
  def scenesToProjectScenes(
      scenes: List[Scene],
      projectId: UUID): ConnectionIO[List[Scene.ProjectScene]] = {
    // "The astute among you will note that we don’t actually need a monad to do this;
    // an applicative functor is all we need here."
    // let's roll, doobie
    val componentsIO: ConnectionIO[
      (List[Thumbnail], List[Datasource], List[SceneToProject])] = {
      val thumbnails = SceneWithRelatedDao.getScenesThumbnails(scenes map {
        _.id
      })
      val datasources = SceneWithRelatedDao.getScenesDatasources(scenes map {
        _.datasource
      })
      val sceneToProjects = SceneWithRelatedDao.getSceneToProjects(scenes map {
        _.id
      }, projectId)
      (thumbnails, datasources, sceneToProjects).tupled
    }

    componentsIO map {
      case (thumbnails, datasources, sceneToProjects) => {
        val groupedThumbs = thumbnails.groupBy(_.sceneId)
        scenes map { scene: Scene =>
          scene.projectSceneFromComponents(
            groupedThumbs.getOrElse(scene.id, List.empty[Thumbnail]),
            datasources.filter(_.id == scene.datasource).head,
            sceneToProjects.find(_.sceneId == scene.id)
          )
        }
      }
    }
  }

}
