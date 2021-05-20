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
    "note"
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
    (fr"INSERT INTO" ++ tableF ++ fr"(" ++ insertFieldsF ++ fr")" ++
      fr"""VALUES
      (uuid_generate_v4(), now(), now(), NULL, ${fromStatus}, NULL, ${taskSessionCreate.sessionType},
       ${user.id}, ${taskId}, NULL)
    """).update.withUniqueGeneratedKeys[TaskSession](
      fieldNames: _*
    )

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
        }
      )

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
        if (
          task.status == TaskStatus.Invalid || task.status == TaskStatus.Split
        ) {
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

  def getRandomTaskSession(
      user: User,
      annotationProjectParams: AnnotationProjectQueryParameters,
      annotationProjectIdOpt: Option[UUID],
      limit: Int,
      taskParams: TaskQueryParameters
  ): ConnectionIO[Option[TaskSession]] = {
    val sessionCreate =
      taskParams.status.toList.contains(TaskStatus.Unlabeled) match {
        case true => TaskSession.Create(TaskSessionType.LabelSession)
        case _    => TaskSession.Create(TaskSessionType.ValidateSession)
      }
    for {
      annotationProjectIds <-
        AnnotationProjectDao
          .authQuery(
            user,
            ObjectType.AnnotationProject,
            None,
            None,
            None
          )
          .filter(annotationProjectParams)
          .filter(annotationProjectIdOpt)
          .list(limit) map { projects =>
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
      .filter(fr"task_id = ${taskId}")
      .filter(excludeSessionIdsOpt match {
        case Some(excludeSessionIds) if excludeSessionIds.size > 0 =>
          fr"id NOT in ${excludeSessionIds.map(id => fr"${id}").intercalate(fr",")}"
        case _ => fr""
      })
      .list
}
