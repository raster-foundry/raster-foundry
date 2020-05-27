package com.rasterfoundry.database

import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.datamodel.Datasource

import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import doobie.postgres.circe.jsonb.implicits._
import doobie.postgres.implicits._

import java.util.UUID

object ProjectLayerDatasourcesDao extends Dao[Datasource] {
  val tableName = """scenes_to_layers sl
                     INNER JOIN scenes s ON sl.scene_id = s.id
                     INNER JOIN datasources d on s.datasource = d.id"""
  val selectF = fr"""
      SELECT DISTINCT ON (d.id)
        d.id, d.created_at, d.created_by, d.modified_at, d.owner,
        d.name, d.visibility, d.composites, d.extras, d.bands, d.license_name
          FROM""" ++ tableF
  def listProjectLayerDatasources(
      projectLayerId: UUID
  ): ConnectionIO[List[Datasource]] = {
    query.filter(fr"sl.project_layer_id=$projectLayerId").list
  }
}
