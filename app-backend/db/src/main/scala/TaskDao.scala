package com.rasterfoundry.database

import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.datamodel._

import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._

import java.time.Instant
import java.util.UUID

object TaskDao extends Dao[Task] {

  val tableName = "tasks"

  val selectF: Fragment =
    fr"""
     SELECT
      id,
      created_at,
      created_by,
      modified_at,
      modified_by,
      project_id,
      project_layer_id,
      status,
      locked_by,
      locked_on,
      geometry
    FROM""" ++ tableF

  val geoJSONSelectF: Fragment =
    fr"""
      SELECT
        id,
        json_object_agg('id', id) ||
            json_object_agg('createdAt', created_at) ||
            json_object_agg('createdBy', created_by) ||
            json_object_agg('modifiedAt', modified_at) ||
            json_object_agg('modifiedBy', modified_by) ||
            json_object_agg('projectId', project_id) ||
            json_object_agg('projectLayerId', project_layer_id) ||
            json_object_agg('status', status) ||
            json_object_agg('lockedBy', locked_by) ||
            json_object_agg('lockedOn', locked_on),
        geometry
    """ ++ tableF

  val insertF: Fragment =
    fr"INSERT INTO " ++ tableF ++ fr"""(
          id,
          created_at,
          created_by,
          modified_at,
          modified_by,
          project_id,
          project_layer_id,
          status,
          locked_by,
          locked_on,
          geometry
     )
     """

  def getTaskById(taskId: UUID): ConnectionIO[Option[Task]] =
    query.filter(taskId).selectOption

  def unsafeGetTaskById(taskId: UUID): ConnectionIO[Task] =
    query.filter(taskId).select

  def toFragment(
      tfc: Task.TaskFeatureCreate,
      user: User
  ): Fragment = {
    fr"""(
        ${UUID.randomUUID}, ${Instant.now}, ${user.id}, ${Instant.now}, ${user.id},
        ${tfc.properties.projectId}, ${tfc.properties.projectLayerId}", ${tfc.properties.status},
        null, null, ${tfc.geometry}
    )"""
  }

  def insertTasks(
      taskFeatureCollection: Task.TaskFeatureCollectionCreate,
      user: User
  ): ConnectionIO[Task.TaskFeatureCollection] = {
    val featureInserts = taskFeatureCollection.features map {
      toFragment(_, user)
    }
    featureInserts.toNel map { inserts =>
      (insertF ++ fr"VALUES (" ++ inserts.intercalate(fr",") ++ fr")").update
        .withGeneratedKeys[Task](
          "id",
          "created_at",
          "created_by",
          "modified_at",
          "modified_by",
          "project_id",
          "project_layer_id",
          "status",
          "locked_by",
          "locked_on",
          "geometry"
        )
        .compile
        .toList map { (xs: List[Task]) =>
        Task.TaskFeatureCollection(
          "FeatureCollection",
          xs.map(_.toGeoJSONFeature)
        )
      }
    } getOrElse {
      Task
        .TaskFeatureCollection("FeatureCollection",
                               List.empty[Task.TaskFeature])
        .pure[ConnectionIO]
    }
  }
}
