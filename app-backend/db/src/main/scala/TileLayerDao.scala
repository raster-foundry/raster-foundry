package com.rasterfoundry.database

import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.datamodel._

import doobie._
import doobie.implicits._
import doobie.postgres.implicits._

import java.util.UUID

object TileLayerDao extends Dao[TileLayer] {
  val tableName = "tiles"

  override val fieldNames = List(
    "id",
    "name",
    "url",
    "is_default",
    "is_overlay",
    "layer_type",
    "annotation_project_id"
  )

  def selectF: Fragment = fr"SELECT" ++ selectFieldsF ++ fr"FROM" ++ tableF

  def insertTileLayer(
      layerCreate: TileLayer.Create,
      annotationProject: AnnotationProject
  ): ConnectionIO[TileLayer] = {
    val isDefault: Boolean = layerCreate.default getOrElse false
    val isOverlay: Boolean = layerCreate.overlay getOrElse false
    (fr"INSERT INTO" ++ tableF ++ fr"""
      (id, name, url, is_default, is_overlay, layer_type, annotation_project_id)
    VALUES (
      uuid_generate_v4(), ${layerCreate.name}, ${layerCreate.url},
      ${isDefault}, ${isOverlay},
      ${layerCreate.layerType}, ${annotationProject.id}
    )""").update.withUniqueGeneratedKeys[TileLayer](
      fieldNames: _*
    )
  }

  def listByProjectId(
      projectId: UUID
  ): ConnectionIO[List[TileLayer]] = {
    (selectF ++ Fragments.whereAndOpt(
      Some(fr"annotation_project_id = ${projectId}")
    )).query[TileLayer].to[List]
  }

  def deleteByProjectId(
      projectId: UUID
  ): ConnectionIO[Int] =
    query.filter(fr"annotation_project_id = $projectId").delete

  def copyTileLayersForProject(
      fromProject: UUID,
      toProject: UUID
  ): ConnectionIO[Int] = {
    (fr"""
           INSERT INTO""" ++ tableF ++ fr"(" ++ insertFieldsF ++ fr")" ++
      fr"""SELECT
           uuid_generate_v4(), name, url, is_default, is_overlay, layer_type, ${toProject}
           FROM""" ++ tableF ++ fr"""
           WHERE annotation_project_id = ${fromProject}
       """).update.run
  }
}
