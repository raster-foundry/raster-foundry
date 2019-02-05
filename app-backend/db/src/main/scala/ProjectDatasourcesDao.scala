package com.rasterfoundry.database

import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.common.datamodel._

import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.postgres.circe.jsonb.implicits._

import java.util.UUID

object ProjectDatasourcesDao extends Dao[Datasource] {
  val tableName = """scenes_to_projects sp
                     INNER JOIN scenes s ON sp.scene_id = s.id
                     INNER JOIN datasources d on s.datasource = d.id"""
  val selectF = fr"""
      SELECT
        distinct(d.id), d.created_at, d.created_by, d.modified_at, d.modified_by, d.owner,
        d.name, d.visibility, d.composites, d.extras, d.bands, d.license_name
          FROM""" ++ tableF
  def listProjectDatasources(
      projectId: UUID): ConnectionIO[List[Datasource]] = {
    query.filter(fr"sp.project_id=$projectId").list
  }
}
