package com.rasterfoundry.database

import com.rasterfoundry.database.Config.taskSessionTtlConfig
import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.datamodel.TaskSessionType.LabelSession
import com.rasterfoundry.datamodel.TaskSessionType.ValidateSession
import com.rasterfoundry.datamodel._

import cats.Applicative
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import doobie.postgres.implicits._

import java.util.UUID

object TaskSessionDao extends Dao[TaskSession] {

  val tableName = "task_sessions"

  override val fieldNames = List(
    "id",
    "created_at",
    "last_tick_at",
    "completed_at",
    "from_status",
    "to_status",
    "session_type",
    "user_id",
    "task_id",
    "note",
    "previous_session_id"
  )

  def selectF: Fragment = fr"SELECT " ++ selectFieldsF ++ fr" FROM " ++ tableF

  def getTaskSessionById(
      id: UUID
  ): ConnectionIO[Option[TaskSession]] =
    query.filter(id).selectOption

  def unsafeGetTaskSessionById(
      id: UUID
  ): ConnectionIO[TaskSession] =
    query.filter(id).select

  def insertTaskSession(
      taskSessionCreate: TaskSession.Create,
      user: User,
      fromStatus: TaskStatus,
      taskId: UUID
  ): ConnectionIO[TaskSession] =
    for {
      latestSession <- getLatestForTask(taskId)
      inserted <- (fr"INSERT INTO" ++ tableF ++ fr"(" ++ insertFieldsF ++ fr")" ++
        fr"""VALUES
      (uuid_generate_v4(), now(), now(), NULL, ${fromStatus}, NULL, ${taskSessionCreate.sessionType},
       ${user.id}, ${taskId}, NULL, ${latestSession map { _.id }})
    """).update.withUniqueGeneratedKeys[TaskSession](
        fieldNames: _*
      )
    } yield inserted

  def insert(
      taskSessionCreate: TaskSession.Create,
      user: User,
      taskId: UUID
  ): ConnectionIO[Option[TaskSession]] =
    TaskDao
      .getTaskById(taskId)
      .flatMap(taskOpt =>
        taskOpt traverse { task =>
          TaskSessionDao.insertTaskSession(
            taskSessionCreate,
            user,
            task.status,
            taskId
          )
      })

  def keepTaskSessionAlive(id: UUID): ConnectionIO[Int] =
    (fr"UPDATE " ++ tableF ++ fr"""SET
      last_tick_at = now()
    WHERE
      id = $id
    """).update.run;

  def completeTaskSession(
      id: UUID,
      taskSessionComplete: TaskSession.Complete
  ): ConnectionIO[Int] = {
    val noteUpdateF = taskSessionComplete.note match {
      case Some(note) => fr"note = ${note.toString},"
      case _          => fr""
    }
    (fr"UPDATE " ++ tableF ++ fr"SET" ++ noteUpdateF ++ fr"""
      to_status = ${taskSessionComplete.toStatus},
      completed_at = now()
      WHERE
        id = ${id}
    """).update.run;
  }

  def hasActiveSessionByTaskId(taskId: UUID): ConnectionIO[Boolean] =
    query
      .filter(fr"task_id = ${taskId}")
      .filter(fr"completed_at is NULL")
      .filter(
        Fragment.const(
          s"last_tick_at + INTERVAL '${taskSessionTtlConfig.taskSessionTtlSeconds} seconds' > now()"
        )
      )
      .exists

  def isSessionTypeMatchTaskStatus(
      taskId: UUID,
      sessionType: TaskSessionType
  ): ConnectionIO[Boolean] =
    for {
      taskOpt <- TaskDao.getTaskById(taskId)
      isMatch <- taskOpt traverse { task =>
        if (task.status == TaskStatus.Invalid || task.status == TaskStatus.Split) {
          Applicative[ConnectionIO].pure(false)
        } else {
          sessionType match {
            case TaskSessionType.LabelSession =>
              Applicative[ConnectionIO].pure(
                task.status == TaskStatus.Unlabeled ||
                  task.status == TaskStatus.LabelingInProgress ||
                  task.status == TaskStatus.Labeled ||
                  task.status == TaskStatus.Flagged
              )
            case TaskSessionType.ValidateSession =>
              Applicative[ConnectionIO].pure(
                task.status == TaskStatus.Labeled ||
                  task.status == TaskStatus.ValidationInProgress ||
                  task.status == TaskStatus.Validated ||
                  task.status == TaskStatus.Flagged
              )
          }
        }
      }
    } yield {
      isMatch match {
        case Some(result) => result
        case _            => false
      }
    }

  def isToStatusMatchTaskSession(
      id: UUID,
      toStatus: TaskStatus
  ): ConnectionIO[Boolean] =
    for {
      sessionOpt <- getTaskSessionById(id)
      isMatch <- sessionOpt traverse { session =>
        session.sessionType match {
          case LabelSession =>
            Applicative[ConnectionIO].pure(
              toStatus == TaskStatus.Unlabeled ||
                toStatus == TaskStatus.Split ||
                toStatus == TaskStatus.Flagged ||
                toStatus == TaskStatus.Labeled
            )

          case ValidateSession =>
            Applicative[ConnectionIO].pure(
              toStatus == TaskStatus.Labeled ||
                toStatus == TaskStatus.Validated ||
                toStatus == TaskStatus.Flagged ||
                toStatus == TaskStatus.Invalid
            )
        }
      }
    } yield {
      isMatch match {
        case Some(result) => result
        case _            => false
      }
    }

  def authorized(
      taskId: UUID,
      user: User,
      actionType: ActionType
  ): ConnectionIO[Boolean] =
    for {
      taskOpt <- TaskDao.getTaskById(taskId)
      projectOpt <- taskOpt flatTraverse { task =>
        AnnotationProjectDao.getById(task.annotationProjectId)
      }
      authProject <- taskOpt traverse { task =>
        AnnotationProjectDao.authorized(
          user,
          ObjectType.AnnotationProject,
          task.annotationProjectId,
          actionType
        )
      }
      authCampaign <- (projectOpt flatTraverse { project =>
        project.campaignId traverse { campaignId =>
          CampaignDao
            .authorized(user, ObjectType.Campaign, campaignId, actionType)
        }
      })
    } yield {
      (authProject, authCampaign) match {
        case (Some(authedProject), None)  => authedProject.toBoolean
        case (None, Some(authedCampaign)) => authedCampaign.toBoolean
        case (Some(authedProject), Some(authedCampaign)) =>
          authedProject.toBoolean && authedCampaign.toBoolean
        case _ => false
      }
    }

  def isOwner(
      taskId: UUID,
      sessionID: UUID,
      user: User
  ): ConnectionIO[Boolean] =
    query
      .filter(fr"user_id = ${user.id}")
      .filter(fr"task_id = ${taskId}")
      .filter(fr"id = ${sessionID}")
      .exists

  def isSessionActive(sessionId: UUID): ConnectionIO[Boolean] =
    query
      .filter(fr"id = ${sessionId}")
      .filter(fr"completed_at is NULL")
      .filter(
        Fragment.const(
          s"last_tick_at + INTERVAL '${taskSessionTtlConfig.taskSessionTtlSeconds} seconds' > now()"
        )
      )
      .exists

  def authWriteLabelToSession(
      taskId: UUID,
      sessionId: UUID,
      user: User
  ): ConnectionIO[Boolean] =
    for {
      auth1 <- TaskSessionDao
        .authorized(
          taskId,
          user,
          ActionType.Annotate
        )
      auth2 <- TaskSessionDao.isOwner(taskId, sessionId, user)
      auth3 <- TaskSessionDao.isSessionActive(sessionId)
      auth4 <- TaskSessionDao.getTaskSessionById(sessionId)
    } yield {
      auth1 && auth2 && auth3 && auth4.map(session => session.taskId) == Some(
        taskId
      )
    }

  def getRandomTaskSession(
      user: User,
      annotationProjectParams: AnnotationProjectQueryParameters,
      annotationProjectIdOpt: Option[UUID],
      limit: Long,
      taskParams: TaskQueryParameters
  ): ConnectionIO[Option[TaskSession]] = {
    val sessionCreate =
      taskParams.status.toList.contains(TaskStatus.Unlabeled) match {
        case true => TaskSession.Create(TaskSessionType.LabelSession)
        case _    => TaskSession.Create(TaskSessionType.ValidateSession)
      }
    for {
      annotationProjectIds <- AnnotationProjectDao
        .authQuery(
          user,
          ObjectType.AnnotationProject,
          None,
          None,
          None
        )
        .filter(annotationProjectParams)
        .filter(annotationProjectIdOpt)
        .list(limit.toInt) map { projects =>
        projects map { _.id }
      }
      taskOpt <- annotationProjectIds.toNel flatTraverse { projectIds =>
        TaskDao.randomTask(taskParams, projectIds, true)
      }
      sessionOpt <- taskOpt traverse { task =>
        insertTaskSession(
          sessionCreate,
          user,
          task.properties.status,
          task.id
        )
      }
    } yield sessionOpt
  }

  def listSessionsByTask(
      taskId: UUID,
      excludeSessionIdsOpt: Option[List[UUID]]
  ): ConnectionIO[List[TaskSession]] =
    query
      .filter(fr"task_sessions.task_id = ${taskId}")
      .filter(excludeSessionIdsOpt match {
        case Some(excludeSessionIds) if excludeSessionIds.size > 0 =>
          Some(
            fr"task_sessions.id NOT in (${excludeSessionIds.map(id => fr"${id}").intercalate(fr",")})"
          )
        case _ => None
      })
      .list

  def getLatestForTask(taskId: UUID): ConnectionIO[Option[TaskSession]] =
    listSessionsByTask(taskId, None) map { sessions =>
      sessions
        .collect({
          case t if !t.completedAt.isEmpty => t
        })
        .sortBy(session =>
          session.completedAt.map(-_.toInstant.getEpochSecond()))
        .headOption
    }

  def listActiveLabels(
      sessionId: UUID
  ): ConnectionIO[List[AnnotationLabelWithClasses]] =
    AnnotationLabelDao.withClassesQB
      .filter(fr"session_id=$sessionId")
      .filter(fr"is_active=true")
      .list

  def filterLabel(
      sessionId: UUID,
      labelId: UUID
  ): Dao.QueryBuilder[AnnotationLabelWithClasses] =
    AnnotationLabelDao.withClassesQB
      .filter(fr"id = ${labelId}")
      .filter(fr"session_id = ${sessionId}")

  def getLabel(
      sessionId: UUID,
      labelId: UUID
  ): ConnectionIO[Option[AnnotationLabelWithClasses]] =
    filterLabel(sessionId, labelId).selectOption

  def unsafeGetLabel(
      sessionId: UUID,
      labelId: UUID
  ): ConnectionIO[AnnotationLabelWithClasses] =
    filterLabel(sessionId, labelId).select

  def insertLabels(
      taskId: UUID,
      sessionId: UUID,
      labels: List[AnnotationLabelWithClasses.Create],
      user: User
  ): ConnectionIO[List[AnnotationLabelWithClasses]] = {
    // always insert labels to the session passed down, which is the current session
    val toInsert = labels.map(_.copy(sessionId = Some(sessionId)))
    for {
      // it is okay to use the unsafe version of the method bc we know task
      // exists in a check from the authWriteLabelToSession method
      dbtask <- TaskDao.unsafeGetTaskById(taskId)
      inserted <- AnnotationLabelDao
        .insertAnnotations(
          dbtask.annotationProjectId,
          taskId,
          toInsert,
          user
        )
    } yield inserted
  }

  def updateLabel(
      taskId: UUID,
      sessionId: UUID,
      labelId: UUID,
      label: AnnotationLabelWithClasses,
      user: User
  ): ConnectionIO[Option[AnnotationLabelWithClasses]] = {
    // updating a label:
    // 1. from the same session: overwrite the same label
    // 2. from previous session: deactivate previous label, insert a new label
    if (label.annotationTaskId != taskId) {
      Option.empty.pure[ConnectionIO]
    } else if (label.sessionId == Some(sessionId)) {
      for {
        _ <- AnnotationLabelDao.updateLabelById(labelId, label)
        updatedLabel <- getLabel(sessionId, labelId)
      } yield updatedLabel
    } else {
      for {
        _ <- AnnotationLabelDao.toggleByActiveLabelId(labelId, false)
        inserted <- insertLabels(
          taskId,
          sessionId,
          List(label.toCreate),
          user
        )
      } yield inserted.headOption
    }
  }

  def deleteLabelsFromSession(sessionId: UUID): ConnectionIO[Int] =
    AnnotationLabelDao.withClassesQB.filter(fr"session_id = $sessionId").delete

  def deleteLabel(
      taskId: UUID,
      sessionId: UUID,
      labelId: UUID
  ): ConnectionIO[Int] =
    // if label is from this session, remove it
    // otherwise, deactivate it
    for {
      labelO <- AnnotationLabelDao.withClassesQB
        .filter(fr"id = ${labelId}")
        .selectOption
      row <- labelO match {
        case Some(label) if label.annotationTaskId == taskId =>
          if (label.sessionId == Some(sessionId))
            filterLabel(sessionId, labelId).delete
          else AnnotationLabelDao.toggleByActiveLabelId(labelId, false)
        case _ => (-1).pure[ConnectionIO]
      }
    } yield row

  def bulkReplaceLabelsInSession(
      taskId: UUID,
      sessionId: UUID,
      labels: List[AnnotationLabelWithClasses],
      user: User
  ): ConnectionIO[List[AnnotationLabelWithClasses]] =
    for {
      _ <- deleteLabelsFromSession(sessionId)
      replaced <- insertLabels(taskId, sessionId, labels.map(_.toCreate), user)
    } yield replaced
}
