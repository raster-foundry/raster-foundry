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
  val joinTableF =
    Fragment.const(
      "tasks left join task_actions on tasks.id = task_actions.task_id"
    )

  val cols =
    fr"""
     SELECT
      distinct(id),
      created_at,
      created_by,
      modified_at,
      owner,
      project_id,
      project_layer_id,
      status,
      locked_by,
      locked_on,
      geometry
    FROM
    """

  val selectF: Fragment =
    cols ++ tableF

  val listF: Fragment =
    cols ++ joinTableF

  val insertF: Fragment =
    fr"INSERT INTO " ++ tableF ++ fr"""(
          id,
          created_at,
          created_by,
          modified_at,
          owner,
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

  def setLockF(taskId: UUID, user: User): Fragment =
    fr"UPDATE " ++ tableF ++ fr"""SET
      locked_by = ${user.id},
      locked_on = now()
      where id = $taskId"""

  def deleteLockF(taskId: UUID): Fragment =
    fr"UPDATE " ++ tableF ++ fr"""SET
      locked_by = null,
      locked_on = null
      where id = $taskId"""

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

  def tasksForProjectAndLayerQB(
      queryParams: TaskQueryParameters,
      projectId: UUID,
      layerId: UUID
  ): Dao.QueryBuilder[Task] =
    Dao
      .QueryBuilder[Task](
        listF,
        joinTableF,
        Nil,
        Some(fr"SELECT count(distinct id) FROM" ++ joinTableF)
      )
      .filter(queryParams)
      .filter(fr"project_id = $projectId")
      .filter(fr"project_layer_id = $layerId")

  def listTasks(
      queryParams: TaskQueryParameters,
      projectId: UUID,
      layerId: UUID,
      pageRequest: PageRequest
  ): ConnectionIO[PaginatedGeoJsonResponse[Task.TaskFeature]] =
    for {
      paginatedResponse <- tasksForProjectAndLayerQB(
        queryParams,
        projectId,
        layerId
      ).page(pageRequest)
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
          "owner",
          "project_id",
          "project_layer_id",
          "status",
          "locked_by",
          "locked_on",
          "geometry"
        )
        .compile
        .toList map { (tasks: List[Task]) =>
        Task.TaskFeatureCollection(
          "FeatureCollection",
          tasks.map(_.toGeoJSONFeature(Nil))
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

  def insertTasksByGrid(
      taskProperties: Task.TaskPropertiesCreate,
      taskGridFeatureCreate: Task.TaskGridFeatureCreate,
      user: User
  ): ConnectionIO[Int] = {
    (insertF ++ fr"""
        SELECT
          uuid_generate_v4(),
          NOW(),
          ${user.id},
          NOW(),
          ${user.id},
          ${taskProperties.projectId},
          ${taskProperties.projectLayerId},
          ${taskProperties.status},
          null,
          null,
          cell
        FROM (
          SELECT (
            ST_Dump(
              ST_MakeGrid(
                ${taskGridFeatureCreate.geometry},
                ${taskGridFeatureCreate.properties.xSizeMeters},
                ${taskGridFeatureCreate.properties.ySizeMeters}
              )
            )
          ).geom AS cell
        ) q
    """).update.run
  }

  def isLockingUserOrUnlocked(taskId: UUID, user: User): ConnectionIO[Boolean] =
    OptionT(getTaskById(taskId))
      .flatMap({ task =>
        OptionT.fromOption(task.lockedBy map { _ == user.id })
      })
      .value map {
      case Some(test) => test
      case _          => true
    }

  def lockTask(
      taskId: UUID
  )(user: User): ConnectionIO[Option[Task.TaskFeature]] =
    setLockF(taskId, user).update.run *> getTaskWithActions(taskId)

  def unlockTask(taskId: UUID): ConnectionIO[Option[Task.TaskFeature]] =
    deleteLockF(taskId).update.run *> getTaskWithActions(taskId)

  def deleteLayerTasks(projectId: UUID, layerId: UUID): ConnectionIO[Int] = {
    (fr"DELETE FROM " ++ this.tableF ++ fr"WHERE project_id = ${projectId} and project_layer_id = ${layerId}").update.run
  }

  def getTeamUsersF(
      projectId: UUID,
      params: UserTaskActivityParameters
  ): Fragment =
    fr"""
    SELECT DISTINCT
      ugr.user_id,
      COALESCE(
        NULLIF(users.name, ''),
        NULLIF(users.email, ''),
        NULLIF(users.personal_info->>'email', ''),
        users.id
      ),
      users.profile_image_uri
    FROM user_group_roles AS ugr
    INNER JOIN (
      SELECT
        unnest(
          ARRAY[
            uuid((projects.extras->'annotate')::jsonb ->>'labelers'),
            uuid((projects.extras->'annotate')::jsonb ->>'validators')
          ]
        ) AS team_id
      FROM projects
      WHERE id = $projectId
    ) AS teams
    ON ugr.group_id = teams.team_id
    LEFT JOIN users
    ON ugr.user_id = users.id
    WHERE ugr.group_type='TEAM' AND ugr.is_active = true
  """ ++ (params.actionUser match {
      case Some(userId) => fr"AND ugr.user_id = $userId"
      case _            => fr""
    })

  def getTaskActionTimeF(
      projectId: UUID,
      layerId: UUID,
      fromStatus: TaskStatus,
      toStatus: TaskStatus,
      params: UserTaskActivityParameters
  ): Fragment =
    fr"""
    SELECT ta.user_id, ta.task_id, ta.from_status, ta.to_status, MAX(ta.timestamp) AS timestamp
    FROM tasks
    LEFT JOIN task_actions AS ta
    ON ta.task_id = tasks.id""" ++ Fragments.whereAndOpt(
      Some(fr"project_id = uuid($projectId)"),
      Some(fr"project_layer_id = uuid($layerId)"),
      Some(fr"ta.from_status = $fromStatus"),
      Some(fr"ta.to_status = $toStatus"),
      params.actionStartTime map { start =>
        fr"ta.timestamp >= $start"
      },
      params.actionEndTime map { end =>
        fr"ta.timestamp <= $end"
      },
      params.actionUser map { userId =>
        fr"ta.user_id = $userId"
      }
    ) ++ fr"GROUP BY (ta.user_id, ta.task_id, ta.from_status, ta.to_status)"

  def getUserTasksF(
      projectId: UUID,
      layerId: UUID,
      action: String,
      params: UserTaskActivityParameters
  ): Fragment = {
    val selectF = fr"""
      SELECT
        to_in_progress.user_id,
        COUNT(DISTINCT to_in_progress.task_id) AS task_count,
        SUM(EXTRACT(EPOCH FROM (to_complete.timestamp - to_in_progress.timestamp))) / COUNT(to_in_progress.task_id) AS task_avg_time
      FROM("""

    val innerJoinF = fr") AS to_in_progress INNER JOIN("

    val joinTargetF = fr""") AS to_complete
      ON
        to_in_progress.user_id = to_complete.user_id
        AND to_in_progress.task_id = to_complete.task_id
      GROUP BY to_in_progress.user_id
    """

    val (inProgressTaskActionTimeF, completeTaskActionTimeF) = action match {
      case "label" =>
        (
          getTaskActionTimeF(
            projectId,
            layerId,
            TaskStatus.Unlabeled,
            TaskStatus.LabelingInProgress,
            params
          ),
          getTaskActionTimeF(
            projectId,
            layerId,
            TaskStatus.LabelingInProgress,
            TaskStatus.Labeled,
            params
          )
        )
      case "validate" =>
        (
          getTaskActionTimeF(
            projectId,
            layerId,
            TaskStatus.Labeled,
            TaskStatus.ValidationInProgress,
            params
          ),
          getTaskActionTimeF(
            projectId,
            layerId,
            TaskStatus.ValidationInProgress,
            TaskStatus.Validated,
            params
          )
        )
    }

    selectF ++ inProgressTaskActionTimeF ++ innerJoinF ++ completeTaskActionTimeF ++ joinTargetF
  }

  def getTaskUserSummary(
      projectId: UUID,
      layerId: UUID,
      params: UserTaskActivityParameters
  ): ConnectionIO[List[TaskUserSummary]] =
    (fr"""
    SELECT
      team_users.user_id,
      team_users.name,
      team_users.profile_image_uri,
      COALESCE(user_labeled_tasks.task_count, 0) AS labeled_task_count,
      COALESCE(user_labeled_tasks.task_avg_time, 0) AS labeled_task_avg_time_second,
      COALESCE(user_validated_tasks.task_count, 0) AS validated_task_count,
      COALESCE(user_validated_tasks.task_avg_time, 0) AS validated_task_avg_time_second
    FROM (""" ++ getTeamUsersF(projectId, params) ++ fr""") AS team_users
    LEFT JOIN (""" ++ getUserTasksF(projectId, layerId, "label", params) ++ fr"""
    ) AS user_labeled_tasks
    ON
      team_users.user_id = user_labeled_tasks.user_id
    LEFT JOIN
    (""" ++ getUserTasksF(projectId, layerId, "validate", params) ++ fr"""
    ) AS user_validated_tasks
    ON
      team_users.user_id = user_validated_tasks.user_id
  """).query[TaskUserSummary].to[List]

  def listLayerTasksByStatus(
      projectId: UUID,
      layerId: UUID,
      taskStatuses: List[String]
  ): ConnectionIO[List[Task]] = {
    val taskStatusF: Fragment =
      taskStatuses.map(TaskStatus.fromString(_)).toNel match {
        case Some(taskStatusNel) => Fragments.in(fr"status", taskStatusNel)
        case _                   => fr""
      }
    query
      .filter(fr"project_id = $projectId")
      .filter(fr"project_layer_id = $layerId")
      .filter(taskStatusF)
      .list
  }

  def taskStatusF(taskStatuses: List[String]): Fragment =
    taskStatuses.map(TaskStatus.fromString(_)).toNel match {
      case Some(taskStatusNel) =>
        fr"AND" ++ Fragments.in(fr"status", taskStatusNel)
      case _ => fr""
    }

  def createUnionedGeomExtent(
      projectId: UUID,
      layerId: UUID,
      taskStatuses: List[String]
  ): ConnectionIO[Option[UnionedGeomExtent]] =
    (fr"""
    SELECT
      ST_Transform(ST_Buffer(ST_Union(ST_Buffer(geometry, 1)), -1), 4326) AS geometry,
      ST_XMin(ST_Extent(ST_Transform(geometry, 4326))) AS x_min,
      ST_YMin(ST_Extent(ST_Transform(geometry, 4326))) AS y_min,
      ST_XMax(ST_Extent(ST_Transform(geometry, 4326))) AS x_max,
      ST_YMax(ST_Extent(ST_Transform(geometry, 4326))) AS y_max
    FROM tasks
    WHERE
      project_id = ${projectId}
      AND project_layer_id = ${layerId}
    """ ++ taskStatusF(taskStatuses))
      .query[UnionedGeomExtent]
      .option

  def listTaskGeomByStatus(
      user: User,
      projectId: UUID,
      layerId: UUID,
      statusO: Option[TaskStatus]
  ): ConnectionIO[PaginatedGeoJsonResponse[Task.TaskFeature]] =
    (fr"""
    SELECT
      status,
      ST_Transform(ST_Buffer(ST_Union(ST_Buffer(geometry, 1)), -1), 4326) AS geometry
    FROM tasks
    WHERE
      project_id = ${projectId}
      AND project_layer_id = ${layerId}
    """ ++ taskStatusF(
      statusO match {
        case Some(status) => List(status.toString())
        case _ =>
          List(
            TaskStatus.Unlabeled.toString,
            TaskStatus.LabelingInProgress.toString,
            TaskStatus.Labeled.toString,
            TaskStatus.ValidationInProgress.toString,
            TaskStatus.Validated.toString
          )
      }
    ) ++ fr"GROUP BY status")
      .query[UnionedGeomWithStatus]
      .to[List]
      .map(geomWithStatusList => {
        PaginatedGeoJsonResponse(
          geomWithStatusList.length,
          false,
          false,
          0,
          geomWithStatusList.length,
          geomWithStatusList.map(geomWithStatus => {
            Task.TaskFeature(
              UUID.randomUUID(),
              Task.TaskProperties(
                UUID.randomUUID(),
                Instant.now(),
                user.id,
                Instant.now(),
                user.id,
                projectId,
                layerId,
                geomWithStatus.status,
                None,
                None,
                List()
              ),
              geomWithStatus.geometry
            )
          })
        )

      })
}
