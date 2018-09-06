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

  def getPermissionsF(id: UUID): Fragment =
    Fragment.const(s"SELECT acrs FROM ${tableName}") ++ Fragments.whereAndOpt(Some(fr"id = ${id}"))

  def addPermissionF(id: UUID, acr: ObjectAccessControlRule): Fragment = Fragment.const(s"""
    UPDATE ${tableName}
    SET acrs = array_append(acrs, '${acr.toObjAcrString}'::text)
  """) ++ Fragments.whereAndOpt((Some(fr"id = ${id}")))

  def addPermissionsManyF(id: UUID, acrList: List[ObjectAccessControlRule]): Fragment = {
    val acrTextArray: String = s"ARRAY[${acrList.map("'" ++ _.toObjAcrString ++ "'").mkString(",")}]"
    Fragment.const(s"""
      UPDATE ${tableName}
      SET acrs = array_cat(acrs, ${acrTextArray})
    """) ++ Fragments.whereAndOpt((Some(fr"id = ${id}")))
  }

  def acrStringsToList(acrs: List[String]): List[Option[ObjectAccessControlRule]] =
    acrs.map(ObjectAccessControlRule.fromObjAcrString)

  def getPermissions(id: UUID): ConnectionIO[List[Option[ObjectAccessControlRule]]] = for {
    isValidObject <- isValidObject(id)
    getPermissions <- isValidObject match {
      case false => throw new Exception(s"Invalid ${tableName} object ${id}")
      case true => getPermissionsF(id).query[List[String]].unique.map(acrStringsToList(_))
    }
  } yield { getPermissions }

  def addPermission(id: UUID, acr: ObjectAccessControlRule): ConnectionIO[List[Option[ObjectAccessControlRule]]] = for {
    permissions <- getPermissions(id)
    permExists = permissions.contains(Some(acr))
    addPermission <- permExists match {
      case true => throw new Exception(s"${acr.toObjAcrString} exists for ${tableName} ${id}")
      case false => addPermissionF(id, acr).update.withUniqueGeneratedKeys[List[String]]("acrs").map(acrStringsToList(_))
    }
  } yield { addPermission }

  def addPermissionsMany(id: UUID, acrList: List[ObjectAccessControlRule]): ConnectionIO[List[Option[ObjectAccessControlRule]]] = for {
    permissions <- getPermissions(id)
    acrListFiltered = acrList.filter(acr => !permissions.contains(Some(acr)))
    addPermissionsMany <- acrListFiltered.length match {
      case 0 => throw new Exception(s"All permissions exist for ${tableName} ${id}")
      case _ => addPermissionsManyF(id, acrListFiltered).update.withUniqueGeneratedKeys[List[String]]("acrs").map(acrStringsToList(_))
    }
  } yield { addPermissionsMany }
}
