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
