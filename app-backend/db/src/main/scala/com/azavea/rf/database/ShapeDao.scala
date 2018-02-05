package com.azavea.rf.database

import com.azavea.rf.database.meta.RFMeta._
import com.azavea.rf.datamodel.Shape

import doobie._, doobie.implicits._
import doobie.postgres._, doobie.postgres.implicits._
import cats._, cats.data._, cats.effect.IO, cats.implicits._

import java.util.UUID


object ShapeDao extends Dao[Shape] {

  val tableName = "shapes"

  val selectF = sql"""
    SELECT
      id, created_at, created_by, modified_at, modified_by, owner,
      organization_id, name, description, geometry
    FROM
  """ ++ tableF

  def select(id: UUID) =
    (selectF ++ fr"WHERE id = $id").query[Shape].unique

}

