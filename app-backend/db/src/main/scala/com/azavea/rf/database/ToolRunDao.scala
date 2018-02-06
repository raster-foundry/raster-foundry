package com.azavea.rf.database

import com.azavea.rf.database.meta.RFMeta._
import com.azavea.rf.datamodel.ToolRun

import doobie._, doobie.implicits._
import doobie.postgres._, doobie.postgres.implicits._
import cats._, cats.data._, cats.effect.IO, cats.implicits._

import java.util.UUID


object ToolRunDao extends Dao[ToolRun] {

  val tableName = "tool_runs"

  val selectF = sql"""
    SELECT
      id, created_at, created_by, modified_at, modified_by, visibility,
      organization, execution_parameters, owner, name
    FROM
  """ ++ tableF

}

