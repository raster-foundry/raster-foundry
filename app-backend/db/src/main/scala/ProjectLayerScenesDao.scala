package com.rasterfoundry.database

import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.datamodel._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.postgres.circe.jsonb.implicits._
import cats._
import cats.implicits._
import com.lonelyplanet.akka.http.extensions.{Order, PageRequest}
import java.util.UUID

import com.rasterfoundry.common.SceneToLayer

object ProjectLayerScenesDao extends Dao[Scene] {
  val tableName =
    "scenes_to_layers s2l INNER JOIN scenes s ON s2l.scene_id = s.id"
  val selectF = fr"""
      SELECT
      s.id, s.created_at, s.created_by, s.modified_at, s.modified_by, s.owner,
          s.visibility, s.tags,
          s.datasource, s.scene_metadata, s.name, s.tile_footprint,
          s.data_footprint, s.metadata_files, s.ingest_location, s.cloud_cover,
          s.acquisition_date, s.sun_azimuth, s.sun_elevation, s.thumbnail_status,
          s.boundary_status, s.ingest_status, s.scene_type FROM""" ++ tableF

  def countLayerScenes(
      projectId: UUID
  ): ConnectionIO[List[(UUID, Int)]] = {
    (Fragment.const(
      """
      | SELECT project_layer_id, count(1)
      | FROM
      | (scenes_to_layers JOIN project_layers ON scenes_to_layers.project_layer_id = project_layers.id) s2lpl
      | JOIN projects ON s2lpl.project_id = projects.id
      """.trim.stripMargin) ++ Fragments.whereAnd(fr"project_id = ${projectId}") ++ fr"GROUP BY project_layer_id")
      .query[(UUID, Int)]
      .to[List]
  }

  def listLayerScenesRaw(
      layerId: UUID,
      splitOptions: SplitOptions): ConnectionIO[List[Scene.ProjectScene]] = {
    val sceneParams = CombinedSceneQueryParams(
      sceneParams = SceneQueryParameters(
        minAcquisitionDatetime = Some(splitOptions.rangeStart),
        maxAcquisitionDatetime = Some(splitOptions.rangeEnd)
      ))
    query
      .filter(fr"project_layer_id = ${layerId}")
      .filter(sceneParams)
      .list
      .flatMap(scenesToProjectScenes(_, layerId))
  }

  def listLayerScenes(
      layerId: UUID,
      pageRequest: PageRequest,
      sceneParams: ProjectSceneQueryParameters
  ): ConnectionIO[PaginatedResponse[Scene.ProjectScene]] = {

    val andPendingF: Option[Fragment] =
      sceneParams.accepted match {
        case Some(true)  => Some(fr"accepted = true")
        case Some(false) => Some(fr"accepted = false")
        case _           => None
      }

    val manualOrder = Map(
      "scene_order" -> Order.Asc,
      "acquisition_date" -> Order.Asc,
      "cloud_cover" -> Order.Asc
    )
    val filterQ = query
      .filter(fr"project_layer_id = ${layerId}")
      .filter(andPendingF)
      .filter(sceneParams)

    val paginatedScenes = for {
      page <- pageRequest.sort.isEmpty match {
        case true  => filterQ.page(pageRequest, manualOrder)
        case false => filterQ.page(pageRequest)
      }
    } yield page
    paginatedScenes.flatMap { (pr: PaginatedResponse[Scene]) =>
      scenesToProjectScenes(pr.results.toList, layerId).map(
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
  @SuppressWarnings(Array("OptionGet"))
  def scenesToProjectScenes(
      scenes: List[Scene],
      layerId: UUID
  ): ConnectionIO[List[Scene.ProjectScene]] = {
    val componentsIO: ConnectionIO[
      (List[Thumbnail], List[Datasource], List[SceneToLayer])
    ] = {
      val thumbnails = SceneWithRelatedDao.getScenesThumbnails(scenes map {
        _.id
      })
      val datasources = SceneWithRelatedDao.getScenesDatasources(scenes map {
        _.datasource
      })
      val sceneToLayers = SceneWithRelatedDao.getScenesToLayers(scenes map {
        _.id
      }, layerId)
      (thumbnails, datasources, sceneToLayers).tupled
    }

    componentsIO map {
      case (thumbnails, datasources, sceneToLayers) => {
        val groupedThumbs = thumbnails.groupBy(_.sceneId)
        scenes map { scene: Scene =>
          scene.projectSceneFromComponents(
            groupedThumbs.getOrElse(scene.id, List.empty[Thumbnail]),
            datasources.find(_.id == scene.datasource).get,
            sceneToLayers.find(_.sceneId == scene.id).map(_.sceneOrder).flatten
          )
        }
      }
    }
  }

}
