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
      "tasks left join task_actions on tasks.id = task_actions.task_id")

  val cols =
    fr"""
     SELECT
      distinct(id),
      created_at,
      created_by,
      modified_at,
      modified_by,
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
          modified_by,
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
        Some(fr"SELECT count(distinct id) FROM" ++ joinTableF))
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
        ${UUID.randomUUID}, ${Instant.now}, ${user.id}, ${Instant.now}, ${user.id}, ${user.id},
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

  def getTaskActionTimeF(projectId: UUID,
                         layerId: UUID,
                         fromStatus: TaskStatus,
                         toStatus: TaskStatus,
                         params: UserTaskActivityParameters): Fragment = {
    val selectTaskActionF =
      fr"SELECT task_actions.user_id, tasks.id as task_id, MAX(task_actions.timestamp) AS timestamp FROM"
    val joinTaskActionF =
      fr"tasks LEFT JOIN task_actions ON task_actions.task_id = tasks.id"
    val whereClause = Fragments
      .whereAndOpt(
        Some(fr"project_id = uuid($projectId)"),
        Some(fr"project_layer_id = uuid($layerId)"),
        Some(fr"task_actions.FROM_status = $fromStatus"),
        Some(fr"task_actions.to_status = $toStatus"),
        params.actionStartTime map { start =>
          fr"task_actions.timestamp >= $start"
        },
        params.actionEndTime map { end =>
          fr"task_actions.timestamp <= $end"
        },
        params.actionUser map { userId =>
          fr"task_actions.user_id = $userId"
        }
      )
    val groupByF =
      fr"GROUP BY (task_actions.user_id, tasks.id, task_actions.FROM_status, task_actions.to_status)"
    fr"(" ++ selectTaskActionF ++ joinTaskActionF ++ whereClause ++ groupByF ++ fr")"
  }

  def getTaskCountAndTimeSpentByActionF(
      projectId: UUID,
      layerId: UUID,
      action: String,
      params: UserTaskActivityParameters): Fragment = {
    val taskSelectF = fr"""
      SELECT
        a.user_id,
        count(a.task_id) AS task_count,
        sum(EXTRACT(EPOCH FROM (b.timestamp - a.timestamp))) / count(a.task_id) AS avg_time
      FROM
    """
    val joinAndGroupF = fr"""
      ON a.user_id = b.user_id AND a.task_id = b.task_id
      GROUP BY a.user_id
    """
    action match {
      case "label" =>
        fr"(" ++ taskSelectF ++
          getTaskActionTimeF(projectId,
                             layerId,
                             TaskStatus.Unlabeled,
                             TaskStatus.LabelingInProgress,
                             params) ++
          fr"AS a INNER JOIN " ++
          getTaskActionTimeF(projectId,
                             layerId,
                             TaskStatus.LabelingInProgress,
                             TaskStatus.Labeled,
                             params) ++
          fr"AS b" ++
          joinAndGroupF ++ fr")"
      case "validate" =>
        fr"(" ++ taskSelectF ++
          getTaskActionTimeF(projectId,
                             layerId,
                             TaskStatus.Labeled,
                             TaskStatus.ValidationInProgress,
                             params) ++
          fr"AS a INNER JOIN " ++
          getTaskActionTimeF(projectId,
                             layerId,
                             TaskStatus.ValidationInProgress,
                             TaskStatus.Validated,
                             params) ++
          fr"AS b" ++
          joinAndGroupF ++ fr")"
    }
  }

  def getTaskUserSummary(
      projectId: UUID,
      layerId: UUID,
      params: UserTaskActivityParameters): ConnectionIO[List[TaskUserSummary]] =
    (
      fr"""
      SELECT
        aa.user_id,
        users.name,
        users.profile_image_uri,
        COALESCE(aa.task_count, 0),
        COALESCE(bb.task_count, 0),
        CASE COALESCE(aa.task_count, 0) + COALESCE(bb.task_count, 0)
           WHEN 0 THEN 0
           ELSE (COALESCE(aa.task_count, 0) * COALESCE(aa.avg_time, 0) + COALESCE(bb.task_count, 0) * COALESCE(bb.avg_time, 0)) / (COALESCE(aa.task_count, 0) + COALESCE(bb.task_count, 0))
        END AS avg_time_spent_second
      FROM
    """ ++
        getTaskCountAndTimeSpentByActionF(projectId, layerId, "label", params) ++
        fr"AS aa FULL OUTER JOIN" ++
        getTaskCountAndTimeSpentByActionF(projectId,
                                          layerId,
                                          "validate",
                                          params) ++
        fr"AS bb ON aa.user_id = bb.user_id INNER JOIN users ON users.id = aa.user_id"
    ).query[TaskUserSummary].to[List]
}
