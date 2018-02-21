package com.azavea.rf.database

import java.sql.Timestamp

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

  def insertShapes(shapes: Seq[Shape.Create], user: User): ConnectionIO[Seq[Shape.GeoJSON]] = {
    val insertSql = """
       INSERT INTO shapes
         (id, created_at, created_by, modified_at, modified_by, owner,
         organization_id, name, description, geometry)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"""

    val insertValues = shapes.map(_.toShape(user))

    Update[Shape](insertSql).updateManyWithGeneratedKeys[Shape](
      "id", "created_at", "created_by", "modified_at", "modified_by",
      "owner", "organization_id", "name", "description", "geometry"
    )(insertValues.toList).compile.toList.map(_.map(_.toGeoJSONFeature))

  }

  def updateShape(updatedShape: Shape.GeoJSON, id: UUID, user: User): ConnectionIO[Int] = {
    val updateTime = new Timestamp(new java.util.Date().getTime())
    val shape = updatedShape.toShape

    val idFilter = fr"id = ${id}"
    (sql"""
       UPDATE shapes
       SET
         modified_at = ${updateTime},
         modified_by = ${user.id},
         name = ${shape.name},
         description = ${shape.description},
         geometry = ${shape.geometry}
       """ ++ Fragments.whereAndOpt(ownerEditFilter(user), Some(idFilter))).update.run
  }

}

