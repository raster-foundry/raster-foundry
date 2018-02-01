package com.azavea.rf.database

import com.azavea.rf.database.meta.RFMeta._
import com.azavea.rf.datamodel.AoiToProject

import doobie._, doobie.implicits._
import doobie.postgres._, doobie.postgres.implicits._
import cats._, cats.data._, cats.effect.IO, cats.implicits._


object AoiToProjectDao {
  object Statements {
    val select = sql"""
      SELECT
        aoi_id, project_id, approval_required, start_time
      FROM
        aois_to_projects
    """
  }
}

