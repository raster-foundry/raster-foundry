package com.rasterfoundry.database

import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.datamodel._

import doobie._
import doobie.implicits._
import doobie.postgres.implicits._

object TileLayerDao extends Dao[TileLayer] {
  val tableName = "tiles"

  def selectF: Fragment = sql"""
    SELECT
      id, name, url, is_default, is_overlay, layer_type, annotation_project_id
    FROM
  """ ++ tableF

  def insertTileLayer(
      layerCreate: TileLayer.Create,
      annotationProject: AnnotationProject
  ): ConnectionIO[TileLayer] =
    (fr"INSERT INTO" ++ tableF ++ fr"""
      (id, name, url, is_default, is_overlay, layer_type, annotation_project_id)
    VALUES (
      uuid_generate_v4(), ${layerCreate.name}, ${layerCreate.url},
      ${layerCreate.default getOrElse false}, ${layerCreate.overlay getOrElse false},
      ${layerCreate.layerType}, ${annotationProject.id}
    )""").update.withUniqueGeneratedKeys[TileLayer](
      "id",
      "name",
      "url",
      "is_default",
      "is_overlay",
      "layer_type",
      "annotation_project_id"
    )
}
