package com.rasterfoundry.database

import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.datamodel._
import com.rasterfoundry.datamodel.GeoJsonCodec.PaginatedGeoJsonResponse

import cats.data.OptionT
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

  def updateF(taskId: UUID, update: Task.TaskFeatureCreate): Fragment =
    fr"UPDATE " ++ tableF ++ fr"""SET
      project_id = ${update.properties.projectId},
      project_layer_id = ${update.properties.projectLayerId},
      status = ${update.properties.status},
      geometry = ${update.geometry}
    WHERE
      id = $taskId
    """;

  def appendAction(
      taskId: UUID,
      initialStatus: TaskStatus,
      newStatus: TaskStatus,
      userId: String
  ): ConnectionIO[Int] =
    if (initialStatus != newStatus) {
      fr"""INSERT INTO task_actions (task_id, user_id, timestamp, from_status, to_status) VALUES (
          $taskId, $userId, now(), $initialStatus, $newStatus
          )""".update.run
    } else {
      0.pure[ConnectionIO]
    }

  def getTaskById(taskId: UUID): ConnectionIO[Option[Task]] =
    query.filter(taskId).selectOption

  def unsafeGetTaskById(taskId: UUID): ConnectionIO[Task] =
    query.filter(taskId).select

  def getTaskActions(taskId: UUID): ConnectionIO[List[TaskActionStamp]] = {
    Dao
      .QueryBuilder[TaskActionStamp](
        fr"select task_id, user_id, timestamp, from_status, to_status FROM task_actions",
        fr"task_actions",
        Nil
      )
      .filter(fr"task_id = $taskId")
      .list
  }

  def getTaskWithActions(taskId: UUID): ConnectionIO[Option[Task.TaskFeature]] =
    (for {
      task <- OptionT(getTaskById(taskId))
      actions <- OptionT.liftF(getTaskActions(task.id))
    } yield { task.toGeoJSONFeature(actions) }).value

  def unsafeGetTaskWithActions(taskId: UUID): ConnectionIO[Task.TaskFeature] =
    for {
      task <- unsafeGetTaskById(taskId)
      actions <- getTaskActions(task.id)
    } yield { task.toGeoJSONFeature(actions) }

  def updateTask(
      taskId: UUID,
      updateTask: Task.TaskFeatureCreate,
      user: User
  ): ConnectionIO[Option[Task.TaskFeature]] =
    (for {
      initial <- OptionT(getTaskById(taskId))
      _ <- OptionT.liftF(updateF(taskId, updateTask).update.run)
      _ <- OptionT.liftF(
        appendAction(
          taskId,
          initial.status,
          updateTask.properties.status,
          user.id
        )
      )
      withActions <- OptionT(getTaskWithActions(taskId))
    } yield withActions).value

  def deleteTask(taskId: UUID): ConnectionIO[Int] =
    query.filter(taskId).delete

  def listTasks(
      queryParams: TaskQueryParameters,
      pageRequest: PageRequest
  ): ConnectionIO[PaginatedGeoJsonResponse[Task.TaskFeature]] =
    for {
      paginatedResponse <- query.filter(queryParams).page(pageRequest)
      withActions <- paginatedResponse.results.toList traverse { feat =>
        unsafeGetTaskWithActions(feat.id)
      }
    } yield {
      PaginatedGeoJsonResponse(
        paginatedResponse.count,
        paginatedResponse.hasPrevious,
        paginatedResponse.hasNext,
        paginatedResponse.page,
        paginatedResponse.pageSize,
        withActions
      )
    }

  def toFragment(
      tfc: Task.TaskFeatureCreate,
      user: User
  ): Fragment = {
    fr"""(
        ${UUID.randomUUID}, ${Instant.now}, ${user.id}, ${Instant.now}, ${user.id},
        ${tfc.properties.projectId}, ${tfc.properties.projectLayerId}, ${tfc.properties.status},
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
      (insertF ++ fr"VALUES " ++ inserts.intercalate(fr",")).update
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
          xs.map(_.toGeoJSONFeature(Nil))
        )
      }
    } getOrElse {
      Task
        .TaskFeatureCollection(
          "FeatureCollection",
          List.empty[Task.TaskFeature]
        )
        .pure[ConnectionIO]
    }
  }
}
