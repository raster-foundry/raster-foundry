package com.azavea.rf.database

import com.azavea.rf.database.meta.RFMeta._
import com.azavea.rf.datamodel.{ToolRun, User}
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import cats._
import cats.data._
import cats.effect.IO
import cats.implicits._
import java.util.UUID

import scala.concurrent.Future


object ToolRunDao extends Dao[ToolRun] {

  val tableName = "tool_runs"

  val selectF = sql"""
    SELECT
      id, created_at, created_by, modified_at, modified_by, visibility,
      organization, execution_parameters, owner, name
    FROM
  """ ++ tableF

  def insertToolRun(newRun: ToolRun.Create, user: User): Future[ToolRun] = ???

}

