package com.rasterfoundry.database

import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.database.util.Page
import com.rasterfoundry.datamodel._
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import doobie.postgres.circe.jsonb.implicits._
import cats._
import cats.data._
import cats.effect.IO
import cats.implicits._

import java.util.UUID

object ProjectLayerDatasourcesDao extends Dao[Datasource] {
  val tableName = """scenes_to_layers sl
                     INNER JOIN scenes s ON sl.scene_id = s.id
                     INNER JOIN datasources d on s.datasource = d.id"""
  val selectF = fr"""
      SELECT
        distinct(d.id), d.created_at, d.created_by, d.modified_at, d.modified_by, d.owner,
        d.name, d.visibility, d.composites, d.extras, d.bands, d.license_name
          FROM""" ++ tableF
  def listProjectLayerDatasources(
      projectId: UUID,
      projectLayerId: UUID
  ): ConnectionIO[List[Datasource]] = {
    query.filter(fr"sl.project_layer_id=$projectLayerId").list
  }
}
