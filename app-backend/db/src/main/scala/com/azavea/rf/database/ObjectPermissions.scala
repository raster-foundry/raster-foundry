package com.azavea.rf.database

import com.azavea.rf.datamodel._
import com.azavea.rf.database.Implicits._
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import cats._
import cats.data._
import cats.effect.IO
import cats.implicits._
import java.util.UUID

trait ObjectPermissions {
  def tableName: String

  def isValidObject(id: UUID): ConnectionIO[Boolean] = (tableName match {
    case "projects"    => ProjectDao
    case "scenes"      => SceneDao
    case "datasources" => DatasourceDao
    case "shapes"      => ShapeDao
    case "workspaces" | "templates" | "analyses" =>
      throw new Exception(s"${tableName} not yet supported")
  }).query.filter(id).exists

  def getPermissions(id: UUID): ConnectionIO[List[Option[ObjectAccessControlRule]]] = for {
    isValidObject <- isValidObject(id)
    getPermissions <- {
      if (isValidObject) {
        (Fragment.const(s"SELECT acrs FROM ${tableName}") ++ Fragments.whereAndOpt(Some(fr"id = ${id}")))
        .query[List[String]]
        .unique
        .map(_.map(ObjectAccessControlRule.fromObjAcrString))
      } else {
        throw new Exception(s"Invalid ${tableName} object ${id}")
      }
    }
  } yield { getPermissions }


  def addPermission(id: UUID, acr: ObjectAccessControlRule): ConnectionIO[List[Option[ObjectAccessControlRule]]] = for {
    permissions <- getPermissions(id)
    permExists = permissions.contains(Some(acr))
    addPermission <- {
      if (!permExists) {
        (Fragment.const(s"""
          UPDATE ${tableName}
          SET acrs = array_append(acrs, '${acr.toObjAcrString}'::text)
        """) ++ Fragments.whereAndOpt((Some(fr"id = ${id}"))))
        .update
        .withUniqueGeneratedKeys[List[String]]("acrs")
        .map(_.map(ObjectAccessControlRule.fromObjAcrString))
      } else {
        throw new Exception(s"${acr.toObjAcrString} exists for ${tableName} ${id}")
      }
    }
  } yield { addPermission }
}
