package com.rasterfoundry.database

import java.sql.Timestamp
import java.util.UUID

import cats.implicits._
import com.rasterfoundry.datamodel._
import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.database.util._
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import doobie.postgres.circe.jsonb.implicits._

object ProjectLayerDao extends Dao[ProjectLayer] {
  val tableName = "project_layers"

  val selectF: Fragment =
    fr"SELECT id, created_at, modified_at, name, project_id, color_group_hex, smart_layer_id, range_start, range_end, geometry" ++ tableF

  def unsafeGetProjectLayerById(
      projectLayerId: UUID): ConnectionIO[ProjectLayer] = {
    query.filter(projectLayerId).select
  }

  def listProjectLayersForProject(
      projectId: UUID): ConnectionIO[List[ProjectLayer]] = {
    (selectF ++ Fragments.whereAndOpt(fr"project_id = ${projectId}".some))
      .query[ProjectLayer]
      .stream
      .compile
      .toList
  }

  def insertProjectLayer(
      pl: ProjectLayer
  ): ConnectionIO[ProjectLayer] = {
    (fr"INSERT INTO" ++ tableF ++ fr"""
    (id, created_at, modified_at, name, project_id, color_group_hex,
    smart_layer_id, range_start, range_end, geometry)
    VALUES
      (${pl.id}, ${pl.createdAt}, ${pl.modifiedAt}, ${pl.name}, ${pl.projectId},
      ${pl.colorGroupHex}, ${pl.smartLayerId}, ${pl.rangeStart}, ${pl.rangeEnd},
      ${pl.geometry})
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
      "geometry"
    )
  }

  def updateProjectLayerQ(projectLayer: ProjectLayer, id: UUID): Update0 = {
    val updateTime = new Timestamp((new java.util.Date()).getTime)
    val idFilter = fr"id = ${projectLayer.id}"
    val query = (fr"UPDATE" ++ tableF ++ fr"""SET
      modified_at = ${updateTime},
      name = ${projectLayer.name},
      colorGroupHex = ${projectLayer.colorGroupHex},
      geometry = ${projectLayer.geometry}
    """ ++ Fragments.whereAndOpt(Some(idFilter))).update
    query
  }

  def createProjectLayer(
      projectId: UUID,
      projectLayer: ProjectLayer
  ): ConnectionIO[ProjectLayer] =
    insertProjectLayer(projectLayer)

  def getProjectLayer(
      projectId: UUID,
      layerId: UUID,
      user: User
  ): ConnectionIO[Option[ProjectLayer]] =
    query.filter(fr"project = ${projectId}").filter(layerId).selectOption

  def deleteProjectLayer(layerId: UUID): ConnectionIO[Int] =
    for {
      deleteCount <- query.filter(layerId).delete
    } yield deleteCount

  def updateProjectLayer(pl: ProjectLayer, plId: UUID): ConnectionIO[Int] = {
    updateProjectLayerQ(pl, plId).run
  }
}
