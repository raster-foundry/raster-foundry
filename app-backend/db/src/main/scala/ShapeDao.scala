package com.rasterfoundry.database

import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.datamodel.{
  Shape,
  User,
  ObjectType,
  GroupType,
  ActionType
}

import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import cats.implicits._
import java.util.UUID

import java.sql.Timestamp

object ShapeDao extends Dao[Shape] with ObjectPermissions[Shape] {

  val tableName = "shapes"

  val selectF = sql"""
    SELECT
      id, created_at, created_by, modified_at, modified_by, owner,
      name, description, geometry
    FROM
  """ ++ tableF

  def unsafeGetShapeById(shapeId: UUID): ConnectionIO[Shape] =
    query.filter(shapeId).select

  def getShapeById(shapeId: UUID): ConnectionIO[Option[Shape]] =
    query.filter(shapeId).selectOption

  def insertShape(shapeCreate: Shape.Create,
                  user: User): ConnectionIO[Shape] = {
    val shape = shapeCreate.toShape(user)
    sql"""
      INSERT INTO shapes
      (id, created_at, created_by, modified_at, modified_by, owner, name, description, geometry)
      VALUES
      (
      ${shape.id}, ${shape.createdAt}, ${shape.createdBy}, ${shape.modifiedAt}, ${shape.modifiedBy},
      ${shape.owner}, ${shape.name}, ${shape.description}, ${shape.geometry}
      )
    """.update.withUniqueGeneratedKeys[Shape](
      "id",
      "created_at",
      "created_by",
      "modified_at",
      "modified_by",
      "owner",
      "name",
      "description",
      "geometry"
    )
  }

  def insertShapes(shapes: Seq[Shape.Create],
                   user: User): ConnectionIO[Seq[Shape.GeoJSON]] = {
    val insertSql =
      """
       INSERT INTO shapes
         (id, created_at, created_by, modified_at, modified_by, owner,
         name, description, geometry)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"""

    val insertValues = shapes.map(_.toShape(user))

    Update[Shape](insertSql)
      .updateManyWithGeneratedKeys[Shape](
        "id",
        "created_at",
        "created_by",
        "modified_at",
        "modified_by",
        "owner",
        "name",
        "description",
        "geometry"
      )(insertValues.toList)
      .compile
      .toList
      .map(_.map(_.toGeoJSONFeature))

  }

  def updateShape(updatedShape: Shape.GeoJSON,
                  id: UUID,
                  user: User): ConnectionIO[Int] = {
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
       """ ++ Fragments.whereAndOpt(Some(idFilter))).update.run
  }

  def authQuery(user: User,
                objectType: ObjectType,
                ownershipTypeO: Option[String] = None,
                groupTypeO: Option[GroupType] = None,
                groupIdO: Option[UUID] = None): Dao.QueryBuilder[Shape] =
    user.isSuperuser match {
      case true =>
        Dao.QueryBuilder[Shape](selectF, tableF, List.empty)
      case false =>
        Dao.QueryBuilder[Shape](selectF,
                                tableF,
                                List(
                                  queryObjectsF(user,
                                                objectType,
                                                ActionType.View,
                                                ownershipTypeO,
                                                groupTypeO,
                                                groupIdO)))
    }

  def authorized(user: User,
                 objectType: ObjectType,
                 objectId: UUID,
                 actionType: ActionType): ConnectionIO[Boolean] =
    this.query
      .filter(authorizedF(user, objectType, actionType))
      .filter(objectId)
      .exists
}
