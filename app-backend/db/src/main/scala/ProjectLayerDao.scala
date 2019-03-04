package com.rasterfoundry.database

import com.rasterfoundry.common.datamodel._
import com.rasterfoundry.database.Implicits._

import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import com.lonelyplanet.akka.http.extensions.PageRequest

import java.sql.Timestamp
import java.util.UUID

object ProjectLayerDao extends Dao[ProjectLayer] {
  val tableName = "project_layers"

  val selectF: Fragment =
    fr"SELECT id, created_at, modified_at, name, project_id, color_group_hex, smart_layer_id, range_start, range_end, geometry, is_single_band, single_band_options from" ++ tableF

  def unsafeGetProjectLayerById(
      projectLayerId: UUID): ConnectionIO[ProjectLayer] = {
    query.filter(projectLayerId).select
  }

  def listProjectLayersForProject(
      page: PageRequest,
      projectId: UUID): ConnectionIO[PaginatedResponse[ProjectLayer]] = {
    query
      .filter(fr"project_id = ${projectId}")
      .page(page)
  }

  def insertProjectLayer(
      pl: ProjectLayer
  ): ConnectionIO[ProjectLayer] = {
    (fr"INSERT INTO" ++ tableF ++ fr"""
    (id, created_at, modified_at, name, project_id, color_group_hex,
    smart_layer_id, range_start, range_end, geometry, is_single_band, single_band_options)
    VALUES
      (${pl.id}, ${pl.createdAt}, ${pl.modifiedAt}, ${pl.name}, ${pl.projectId},
      ${pl.colorGroupHex}, ${pl.smartLayerId}, ${pl.rangeStart}, ${pl.rangeEnd},
      ${pl.geometry}, ${pl.isSingleBand}, ${pl.singleBandOptions})
    """).update.withUniqueGeneratedKeys[ProjectLayer](
      "id",
      "created_at",
      "modified_at",
      "name",
      "project_id",
      "color_group_hex",
      "smart_layer_id",
      "range_start",
      "range_end",
      "geometry",
      "is_single_band",
      "single_band_options"
    )
  }

  def updateProjectLayerQ(projectLayer: ProjectLayer, id: UUID): Update0 = {
    val updateTime = new Timestamp((new java.util.Date()).getTime)
    val idFilter = fr"id = ${id}"
    val query = (fr"UPDATE" ++ tableF ++ fr"""SET
      modified_at = ${updateTime},
      name = ${projectLayer.name},
      color_group_hex = ${projectLayer.colorGroupHex},
      geometry = ${projectLayer.geometry},
      project_id = ${projectLayer.projectId},
      is_single_band = ${projectLayer.isSingleBand},
      single_band_options = ${projectLayer.singleBandOptions}
    """ ++ Fragments.whereAndOpt(Some(idFilter))).update
    query
  }

  def createProjectLayer(
      projectLayer: ProjectLayer
  ): ConnectionIO[ProjectLayer] =
    insertProjectLayer(projectLayer)

  def getProjectLayer(
      projectId: UUID,
      layerId: UUID
  ): ConnectionIO[Option[ProjectLayer]] =
    query.filter(fr"project_id = ${projectId}").filter(layerId).selectOption

  def deleteProjectLayer(layerId: UUID): ConnectionIO[Int] =
    for {
      deleteCount <- query.filter(layerId).delete
    } yield deleteCount

  def updateProjectLayer(pl: ProjectLayer, plId: UUID): ConnectionIO[Int] = {
    updateProjectLayerQ(pl, plId).run
  }

  def layerIsInProject(layerId: UUID,
                       projectID: UUID): ConnectionIO[Boolean] = {
    query.filter(layerId).selectOption map {
      case Some(projectLayer) => projectLayer.projectId == Option(projectID)
      case _                  => false
    }
  }
}
