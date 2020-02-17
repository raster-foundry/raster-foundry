package com.rasterfoundry.database

import com.rasterfoundry.common.SceneToLayer
import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.datamodel._

import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.postgres.circe.jsonb.implicits._
import doobie.postgres.implicits._
import geotrellis.vector.{Geometry, Projected}

import java.util.UUID

object ProjectLayerScenesDao extends Dao[Scene] {
  val tableName =
    "scenes_to_layers s2l INNER JOIN scenes s ON s2l.scene_id = s.id"
  val selectF = fr"""
    SELECT
      s.id, s.created_at, s.created_by, s.modified_at, s.owner,
      s.visibility, s.tags,
      s.datasource, s.scene_metadata, s.name, s.tile_footprint,
      s.data_footprint, s.metadata_files, s.ingest_location, s.cloud_cover,
      s.acquisition_date, s.sun_azimuth, s.sun_elevation, s.thumbnail_status,
      s.boundary_status, s.ingest_status, s.scene_type, s.data_path, s.crs,
      s.band_count, s.cell_type, s.grid_extent, s.resolutions, s.no_data_value
    FROM
  """ ++ tableF

  def countLayerScenes(
      projectId: UUID
  ): ConnectionIO[List[(UUID, Int)]] = {
    (Fragment.const(
      """
      | SELECT project_layer_id, count(1)
      | FROM
      | (scenes_to_layers JOIN project_layers ON scenes_to_layers.project_layer_id = project_layers.id) s2lpl
      | JOIN projects ON s2lpl.project_id = projects.id
      """.trim.stripMargin
    ) ++ Fragments.whereAnd(fr"project_id = ${projectId}") ++ fr"GROUP BY project_layer_id")
      .query[(UUID, Int)]
      .to[List]
  }

  def listLayerScenesRaw(
      layerId: UUID,
      splitOptionsO: Option[SplitOptions] = None
  ): ConnectionIO[List[Scene]] = {
    val sceneParams = splitOptionsO match {
      case Some(splitOptions: SplitOptions) =>
        CombinedSceneQueryParams(
          sceneParams = SceneQueryParameters(
            minAcquisitionDatetime = Some(splitOptions.rangeStart),
            maxAcquisitionDatetime = Some(splitOptions.rangeEnd)
          )
        )
      case _ => CombinedSceneQueryParams()
    }
    query
      .filter(fr"project_layer_id = ${layerId}")
      .filter(sceneParams)
      .list
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

  def getUnionedGeomExtent(
      layerId: UUID
  ): ConnectionIO[Option[UnionedGeomExtent]] =
    (fr"""
    SELECT
      ST_Transform(ST_Collect(s.data_footprint), 4326) AS geometry,
      ST_XMin(ST_Extent(ST_Transform(s.data_footprint, 4326))) AS x_min,
      ST_YMin(ST_Extent(ST_Transform(s.data_footprint, 4326))) AS y_min,
      ST_XMax(ST_Extent(ST_Transform(s.data_footprint, 4326))) AS x_max,
      ST_YMax(ST_Extent(ST_Transform(s.data_footprint, 4326))) AS y_max
    FROM scenes s
    JOIN scenes_to_layers stl
    ON s.id = stl.scene_id
    WHERE stl.project_layer_id = ${layerId}
  """).query[UnionedGeomExtent].option

  def getUnionedGeomFootprint(
      layerId: UUID
  ): ConnectionIO[Option[Projected[Geometry]]] =
    (fr"""
    SELECT
      ST_Transform(ST_Union(s.data_footprint), 4326) AS geometry
    FROM scenes s
    JOIN scenes_to_layers stl
    ON s.id = stl.scene_id
    WHERE stl.project_layer_id = ${layerId}
  """).query[Option[Projected[Geometry]]].unique
}
