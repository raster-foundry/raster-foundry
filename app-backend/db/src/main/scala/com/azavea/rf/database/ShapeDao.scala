package com.azavea.rf.database

import com.azavea.rf.database.meta.RFMeta._
import com.azavea.rf.datamodel.{Shape, User}
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import cats._
import cats.data._
import cats.effect.IO
import cats.implicits._
import java.util.UUID
import com.azavea.rf.database.filter.Filterables._


object ShapeDao extends Dao[Shape] {

  val tableName = "shapes"

  val selectF = sql"""
    SELECT
      id, created_at, created_by, modified_at, modified_by, owner,
      organization_id, name, description, geometry
    FROM
  """ ++ tableF

  def insertShapes(shapes: Seq[Shape.Create], user: User): ConnectionIO[Seq[Shape.GeoJSON]] = ???
  def updateShape(updatedShape: Shape.GeoJSON, id: UUID, user: User): ConnectionIO[Int] = ???

}

