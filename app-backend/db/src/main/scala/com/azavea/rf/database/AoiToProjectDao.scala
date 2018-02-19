package com.azavea.rf.database

import com.azavea.rf.database.meta.RFMeta._
import com.azavea.rf.datamodel.{ AOI, AoiToProject }

import doobie._, doobie.implicits._
import doobie.postgres._, doobie.postgres.implicits._
import cats._, cats.data._, cats.effect.IO, cats.implicits._

import java.util.UUID


object AoiToProjectDao extends Dao[AoiToProject] {
  val tableName = "aois_to_projects"

  val selectF = sql"""
    SELECT
      aoi_id, project_id, approval_required, start_time
    FROM
      aois_to_projects
  """

  def create(aoi: AOI, projectId: UUID): ConnectionIO[AoiToProject] = {
    (fr"INSERT INTO" ++ tableF ++ fr"""
        (aoi_id, project_id, approval_required, start_time)
      VALUES
        (${aoi.id}, ${projectId}, true, NOW())
    """).update.withUniqueGeneratedKeys[AoiToProject](
      "aoi_id", "project_id", "approval_required", "start_time"
    )
  }
}

