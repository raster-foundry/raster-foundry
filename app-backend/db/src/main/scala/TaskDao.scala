package com.rasterfoundry.database

import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.datamodel.GeoJsonCodec.PaginatedGeoJsonResponse
import com.rasterfoundry.datamodel.Task.TaskPropertiesCreate
import com.rasterfoundry.datamodel._

import cats.data.{NonEmptyList, OptionT}
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import doobie.postgres.implicits._
import doobie.refined.implicits._
import eu.timepit.refined.types.string.NonEmptyString
import geotrellis.vector.{Geometry, Projected}
import shapeless._

import scala.concurrent.duration._

import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

object TaskDao extends Dao[Task] with ConnectionIOLogger {

  override val fieldNames = List(
    "id",
    "created_at",
    "created_by",
    "modified_at",
    "owner",
    "status",
    "locked_by",
    "locked_on",
    "geometry",
    "annotation_project_id",
    "task_type",
    "parent_task_id",
    "reviews",
    "review_status"
  )

  type MaybeEmptyUnionedGeomExtent =
    Option[Projected[Geometry]] :: Option[Double] :: Option[Double] :: Option[
      Double
    ] :: Option[Double] :: HNil

  val tableName = "tasks"
  val joinTableF =
    Fragment.const(
      "tasks left join task_actions on tasks.id = task_actions.task_id"
    )

  val selectF: Fragment =
    fr"SELECT" ++ selectFieldsF ++ fr"FROM" ++ tableF

  val listF: Fragment =
    fr"SELECT" ++ selectFieldsF ++ fr"FROM" ++ joinTableF

  val insertF: Fragment =
    fr"INSERT INTO " ++ tableF ++ fr"(" ++ insertFieldsF ++ fr")"

  def updateF(taskId: UUID, update: Task.TaskFeatureCreate): Fragment = {
    val taskTypeF = update.properties.taskType match {
      case Some(taskType) => fr"task_type = ${taskType},"
      case None           => Fragment.empty
    }
    val reviewsF = update.properties.reviews match {
      case Some(reviews) => fr"reviews = ${reviews},"
      case None          => Fragment.empty
    }
    val restF = fr"""
      status = ${update.properties.status},
      geometry = ${update.geometry},
      annotation_project_id = ${update.properties.annotationProjectId},
      parent_task_id = ${update.properties.parentTaskId}
    """

    fr"UPDATE " ++ tableF ++ fr"SET" ++ taskTypeF ++ reviewsF ++ restF ++ fr"""
    WHERE
      id = $taskId
    """;
  }

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
      userId: String,
      note: Option[NonEmptyString]
  ): ConnectionIO[Int] =
    if (initialStatus != newStatus) {
      fr"""INSERT INTO task_actions (task_id, user_id, timestamp, from_status, to_status, note) VALUES (
          $taskId, $userId, now(), $initialStatus, $newStatus, $note
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
        fr"select task_id, user_id, timestamp, from_status, to_status, note FROM task_actions",
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

  def unsafeGetActionsForTask(task: Task): ConnectionIO[Task.TaskFeature] =
    for {
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
          user.id,
          updateTask.properties.note
        )
      )
      withActions <- OptionT(getTaskWithActions(taskId))
    } yield withActions).value

  def deleteTask(taskId: UUID): ConnectionIO[Int] =
    query.filter(taskId).delete

  def tasksForAnnotationProjectQB(
      queryParams: TaskQueryParameters,
      annotationProjectId: UUID
  ): Dao.QueryBuilder[Task] =
    Dao
      .QueryBuilder[Task](
        listF,
        joinTableF,
        Nil,
        Some(fr"SELECT count(distinct id) FROM" ++ joinTableF)
      )
      .filter(queryParams)
      .filter(fr"annotation_project_id = $annotationProjectId")
      .filter(fr"parent_task_id IS NULL")

  def listTasks(
      queryParams: TaskQueryParameters,
      annotationProjectId: UUID,
      pageRequest: PageRequest
  ): ConnectionIO[PaginatedGeoJsonResponse[Task.TaskFeature]] = {
    val actionFiltered = queryParams.actionUser.nonEmpty ||
      queryParams.actionType.nonEmpty ||
      queryParams.actionStartTime.nonEmpty ||
      queryParams.actionEndTime.nonEmpty ||
      queryParams.actionMinCount.nonEmpty ||
      queryParams.actionMaxCount.nonEmpty
    for {
      paginatedResponse <- actionFiltered match {
        case true =>
          tasksForAnnotationProjectQB(
            queryParams,
            annotationProjectId
          ).page(pageRequest)
        case _ =>
          query
            .filter(queryParams)
            .filter(fr"annotation_project_id = $annotationProjectId")
            .filter(fr"parent_task_id IS NULL")
            .page(pageRequest)
      }
      withActions <- paginatedResponse.results.toList traverse { task =>
        unsafeGetActionsForTask(task)
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
  }

  def toFragment(
      tfc: Task.TaskFeatureCreate,
      user: User
  ): Fragment = {
    val t = tfc.properties.taskType.getOrElse(TaskType.fromString("LABEL"))
    val r = tfc.properties.reviews.getOrElse(Map[UUID, Review]())
    fr"""(
        ${UUID.randomUUID}, ${Timestamp.from(Instant.now)}, ${user.id}, ${Timestamp
      .from(Instant.now)},
        ${user.id}, ${tfc.properties.status}, null, null, ${tfc.geometry},
        ${tfc.properties.annotationProjectId}, ${t}, ${tfc.properties.parentTaskId}, ${r},
        ${tfc.properties.reviewStatus}
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
          fieldNames: _*
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
  ): ConnectionIO[Int] =
    for {
      annotationProjectO <- AnnotationProjectDao.getById(
        taskProperties.annotationProjectId
      )
      _ <- debug(
        s"Got annotation project for ${taskProperties.annotationProjectId}"
      )
      geomO <- (taskGridFeatureCreate.geometry, annotationProjectO) match {
        case (Some(g), _) => Option(g).pure[ConnectionIO]
        case (_, Some(annotationProject)) =>
          AnnotationProjectDao.getFootprint(annotationProject.id)
        case _ => None.pure[ConnectionIO]
      }
      taskSizeO = taskGridFeatureCreate.properties.sizeMeters orElse (annotationProjectO flatMap {
        _.taskSizeMeters
      })
      gridInsert <- (geomO, taskSizeO).tupled.map { geomAndSize =>
        val (geom, size) = geomAndSize
        (insertF ++ fr"""
        SELECT
          uuid_generate_v4(),
          NOW(),
          ${user.id},
          NOW(),
          ${user.id},
          ${taskProperties.status},
          null,
          null,
          cell,
          ${taskProperties.annotationProjectId},
          ${TaskType.Label.toString()}::task_type,
          null,
          '{}'::jsonb
        FROM (
          SELECT (
            ST_Dump(
              ST_MakeGrid(
                ${geom},
                ${size},
                ${size}
              )
            )
          ).geom AS cell
        ) q
    """).update.run
      } getOrElse {
        0.pure[ConnectionIO]
      }
      _ <- debug(s"Inserted $gridInsert tasks")
      _ <- annotationProjectO traverse { annotationProject =>
        AnnotationProjectDao.update(
          annotationProject.copy(taskSizeMeters = taskSizeO, aoi = geomO),
          annotationProject.id
        )
      }
    } yield gridInsert

  def isLockingUserOrUnlocked(taskId: UUID, user: User): ConnectionIO[Boolean] =
    OptionT(getTaskById(taskId))
      .flatMap({ task =>
        OptionT.fromOption(task.lockedBy map { _ == user.id })
      })
      .value map {
      case Some(test) => test
      case _          => true
    }

  def hasStatus(
      taskId: UUID,
      statuses: List[TaskStatus]
  ): ConnectionIO[Boolean] =
    getTaskById(taskId)
      .map(_.exists(task => statuses.contains(task.status)))

  def lockTask(
      taskId: UUID
  )(user: User): ConnectionIO[Option[Task.TaskFeature]] =
    setLockF(taskId, user).update.run *> getTaskWithActions(taskId)

  def unlockTask(taskId: UUID): ConnectionIO[Option[Task.TaskFeature]] =
    deleteLockF(taskId).update.run *> getTaskWithActions(taskId)

  def deleteProjectTasks(annotationProjectId: UUID): ConnectionIO[Int] = {
    (fr"DELETE FROM " ++ this.tableF ++ fr"WHERE annotation_project_id = ${annotationProjectId}").update.run
  }

  def getTeamUsersF(
      annotationProjectId: UUID,
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
      ) as name,
      users.profile_image_uri
    FROM user_group_roles AS ugr
    INNER JOIN (
      SELECT
        unnest(
          ARRAY[
            labelers_team_id,
            validators_team_id
          ]
        ) AS team_id
      FROM annotation_projects
      WHERE id = $annotationProjectId
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
      annotationProjectId: UUID,
      fromStatus: TaskStatus,
      toStatus: TaskStatus,
      params: UserTaskActivityParameters
  ): Fragment =
    fr"""
    SELECT ta.user_id, ta.task_id, ta.from_status, ta.to_status, MAX(ta.timestamp) AS timestamp
    FROM tasks
    LEFT JOIN task_actions AS ta
    ON ta.task_id = tasks.id""" ++ Fragments.whereAndOpt(
      Some(fr"annotation_project_id = uuid($annotationProjectId)"),
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
      annotationProjectId: UUID,
      action: String,
      params: UserTaskActivityParameters
  ): Fragment = {
    val joinSelectF = fr"""
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
            annotationProjectId,
            TaskStatus.Unlabeled,
            TaskStatus.LabelingInProgress,
            params
          ),
          getTaskActionTimeF(
            annotationProjectId,
            TaskStatus.LabelingInProgress,
            TaskStatus.Labeled,
            params
          )
        )
      case "validate" =>
        (
          getTaskActionTimeF(
            annotationProjectId,
            TaskStatus.Labeled,
            TaskStatus.ValidationInProgress,
            params
          ),
          getTaskActionTimeF(
            annotationProjectId,
            TaskStatus.ValidationInProgress,
            TaskStatus.Validated,
            params
          )
        )
    }

    joinSelectF ++ inProgressTaskActionTimeF ++ innerJoinF ++ completeTaskActionTimeF ++ joinTargetF
  }

  def getTaskUserSummary(
      annotationProjectId: UUID,
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
    FROM (""" ++ getTeamUsersF(annotationProjectId, params) ++ fr""") AS team_users
    LEFT JOIN (""" ++ getUserTasksF(annotationProjectId, "label", params) ++ fr"""
    ) AS user_labeled_tasks
    ON
      team_users.user_id = user_labeled_tasks.user_id
    LEFT JOIN
    (""" ++ getUserTasksF(annotationProjectId, "validate", params) ++ fr"""
    ) AS user_validated_tasks
    ON
      team_users.user_id = user_validated_tasks.user_id
  """).query[TaskUserSummary].to[List]

  def listProjectTasksByStatus(
      annotationProjectId: UUID,
      taskStatuses: List[String]
  ): ConnectionIO[List[Task]] = {
    query
      .filter(fr"annotation_project_id = $annotationProjectId")
      .filter(taskStatusF(taskStatuses))
      .list
  }

  def taskStatusF(taskStatuses: List[String]): Option[Fragment] =
    taskStatuses.map(TaskStatus.fromString(_)).toNel map { taskStatusNel =>
      Fragments.in(fr"status", taskStatusNel)
    }

  def createUnionedGeomExtent(
      annotationProjectId: UUID,
      taskStatuses: List[String]
  ): ConnectionIO[Option[UnionedGeomExtent]] =
    Dao
      .QueryBuilder[MaybeEmptyUnionedGeomExtent](
        fr"""
    SELECT
      ST_Transform(ST_Buffer(ST_Union(ST_Buffer(geometry, 1)), -1), 4326) AS geometry,
      ST_XMin(ST_Extent(ST_Transform(geometry, 4326))) AS x_min,
      ST_YMin(ST_Extent(ST_Transform(geometry, 4326))) AS y_min,
      ST_XMax(ST_Extent(ST_Transform(geometry, 4326))) AS x_max,
      ST_YMax(ST_Extent(ST_Transform(geometry, 4326))) AS y_max
      FROM tasks
    """,
        fr"tasks",
        Nil,
        None
      )
      .filter(fr"annotation_project_id = $annotationProjectId")
      .filter(taskStatusF(taskStatuses))
      .select map {
      case Some(geom) :: Some(xMin) :: Some(yMin) :: Some(xMax) :: Some(yMax) :: HNil =>
        Some(UnionedGeomExtent(geom, xMin, yMin, xMax, yMax))
      case _ =>
        None
    }

  def listTaskGeomByStatus(
      user: User,
      annotationProjectId: UUID,
      statusO: Option[TaskStatus]
  ): ConnectionIO[PaginatedGeoJsonResponse[Task.TaskFeature]] =
    (fr"""
    SELECT
      status,
      ST_Transform(ST_Buffer(ST_Union(ST_Buffer(geometry, 1)), -1), 4326) AS geometry
    FROM tasks""" ++ Fragments
      .whereAndOpt(
        Some(fr"annotation_project_id = ${annotationProjectId}"),
        taskStatusF(statusO.toList map { _.toString })
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
                Timestamp.from(Instant.now()),
                user.id,
                Timestamp.from(Instant.now()),
                user.id,
                geomWithStatus.status,
                None,
                None,
                List(),
                annotationProjectId,
                None,
                TaskType.Label,
                None,
                Map[UUID, Review](),
                None
                // since this is a fake task feature that exists I think for the purpose of
                // providing a geojson interface over the task status geom summary,
                // it's fine to pretend that the note is always None
              ),
              geomWithStatus.geometry
            )
          })
        )

      })

  def countProjectTaskByStatus(
      projectId: UUID
  ): ConnectionIO[Map[TaskStatus, Int]] = (fr"""
    SELECT status, COUNT(id)
    FROM tasks
    WHERE annotation_project_id = ${projectId}
    GROUP BY status;
    """).query[(TaskStatus, Int)].to[List].map { list =>
    val statusMap = list.toMap
    List(
      TaskStatus.Unlabeled,
      TaskStatus.LabelingInProgress,
      TaskStatus.Labeled,
      TaskStatus.ValidationInProgress,
      TaskStatus.Validated
    ).fproduct(status => statusMap.getOrElse(status, 0)).toMap
  }

  def listTasksByStatus(
      annotationProjectId: UUID,
      taskStatuses: List[String]
  ): ConnectionIO[List[Task]] = {
    query
      .filter(fr"annotation_project_id = $annotationProjectId")
      .filter(taskStatusF(taskStatuses))
      .list
  }

  def copyAnnotationProjectTasks(
      fromProject: UUID,
      toProject: UUID,
      user: User
  ): ConnectionIO[Int] = {
    (fr"""
           INSERT INTO""" ++ tableF ++ fr"(" ++ insertFieldsF ++ fr")" ++
      fr"""SELECT
           uuid_generate_v4(), now(), ${user.id}, now(), ${user.id},
           'UNLABELED', null, null, geometry, ${toProject}, task_type,
           null, '{}'::jsonb, null
           FROM """ ++ tableF ++ fr"""
           WHERE annotation_project_id = ${fromProject} AND parent_task_id IS NULL
      """).update.run
  }

  private def regressTaskStatus(
      taskId: UUID,
      taskStatus: TaskStatus
  ): ConnectionIO[(TaskStatus, Option[NonEmptyString])] =
    taskStatus match {
      // if it's not currently flagged, then it might have come from flagged, which means
      // that two statuses ago it was flagged -- so the note is in the second most recent
      // task action stamp
      case TaskStatus.LabelingInProgress | TaskStatus.ValidationInProgress =>
        getTaskActions(taskId).map({ (stamps: List[TaskActionStamp]) =>
          val sorted = stamps
            .sortBy(stamp => -stamp.timestamp.toInstant.getEpochSecond)
          val previousStatus = sorted.headOption map { _.fromStatus } getOrElse {
            TaskStatus.Unlabeled
          }
          val previousNote = if (previousStatus == TaskStatus.Flagged) {
            sorted.drop(1).headOption flatMap { _.note }
          } else {
            None
          }
          (previousStatus, previousNote)
        })
      // if it's flagged currently, then the note is in the most recent task action stamp
      case TaskStatus.Flagged =>
        getTaskActions(taskId).map({ (stamps: List[TaskActionStamp]) =>
          stamps
            .sortBy(stamp => -stamp.timestamp.toInstant.getEpochSecond)
            .headOption map { stamp =>
            (TaskStatus.Flagged, stamp.note)
          } getOrElse { (TaskStatus.Flagged, None) }
        })
      // if a status is anything else (not flagged, not in progress), then it's safe to
      // return it without a note
      case status => (status, Option.empty[NonEmptyString]).pure[ConnectionIO]
    }

  def expireStuckTasks(taskExpiration: FiniteDuration): ConnectionIO[Int] =
    for {
      _ <- info("Expiring stuck tasks")
      defaultUser <- UserDao.unsafeGetUserById("default")
      stuckTasks <- query
        .filter(
          fr"locked_on <= ${Timestamp.from(Instant.now.minusMillis(taskExpiration.toMillis))}"
        )
        .list
      _ <- stuckTasks traverse { task =>
        regressTaskStatus(task.id, task.status) flatMap {
          case (newStatus, newNote) =>
            val update =
              Task.TaskFeatureCreate(
                TaskPropertiesCreate(
                  newStatus,
                  task.annotationProjectId,
                  newNote,
                  Some(task.taskType),
                  task.parentTaskId,
                  Some(task.reviews),
                  task.reviewStatus
                ),
                task.geometry
              )
            updateTask(task.id, update, defaultUser) <* unlockTask(task.id)
        }
      }
    } yield stuckTasks.length

  def randomTask(
      queryParams: TaskQueryParameters,
      annotationProjectIds: NonEmptyList[UUID]
  ): ConnectionIO[Option[Task.TaskFeature]] = {
    val builder = query
      .filter(queryParams)
      .filter(fr"parent_task_id IS NULL")
      .filter(Fragments.in(fr"annotation_project_id", annotationProjectIds))

    (selectF ++ Fragments.whereAndOpt(builder.filters: _*) ++ fr"ORDER BY RANDOM() LIMIT 1")
      .query[Task]
      .to[List] flatMap { tasks =>
      tasks.toNel traverse { tasks =>
        getTaskWithActions(tasks.head.id)
      }
    } map { _.flatten }
  }

  def children(
      taskId: UUID,
      pageRequest: PageRequest
  ): ConnectionIO[PaginatedGeoJsonResponse[Task.TaskFeature]] = {

    for {
      paginatedResponse <- query
        .filter(fr"parent_task_id = $taskId")
        .page(pageRequest)

      withActions <- paginatedResponse.results.toList traverse { task =>
        unsafeGetActionsForTask(task)
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
  }
}
