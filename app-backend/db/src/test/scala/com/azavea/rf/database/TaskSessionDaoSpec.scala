package com.rasterfoundry.database

import com.rasterfoundry.common.Generators.Implicits._
import com.rasterfoundry.datamodel._

import cats.implicits._
import doobie.implicits._
import org.scalacheck.Prop.forAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.Checkers

class TaskSessionDaoSpec
    extends AnyFunSuite
    with Matchers
    with Checkers
    with DBTestConfig
    with PropTestHelpers {
  test("insert a task session") {
    check {
      forAll(
        (
            userCreate: User.Create,
            annotationProjectCreate: AnnotationProject.Create,
            taskFeaturesCreate: Task.TaskFeatureCollectionCreate,
            taskSessionCreate: TaskSession.Create
        ) => {
          val tfcFirstTaskOnly = taskFeaturesCreate.copy(
            features = taskFeaturesCreate.features.take(1)
          )
          val insertIO = for {
            dbUser <- UserDao.create(userCreate)
            dbAnnotationProj <-
              AnnotationProjectDao
                .insert(
                  annotationProjectCreate.copy(projectId = None),
                  dbUser
                )
            Task.TaskFeatureCollection(_, features) <- TaskDao.insertTasks(
              fixupTaskFeaturesCollection(
                tfcFirstTaskOnly,
                dbAnnotationProj
              ),
              dbUser
            )
            firstTask = features.headOption
            dbTaskSession <- firstTask traverse { task =>
              TaskSessionDao
                .insertTaskSession(
                  taskSessionCreate,
                  dbUser,
                  task.properties.status,
                  task.id
                )
            }
          } yield (dbTaskSession, firstTask, dbUser)

          val (taskSessionOpt, firstTaskOpt, user) =
            insertIO.transact(xa).unsafeRunSync

          val sessionAndTaskOpt = (taskSessionOpt, firstTaskOpt).tupled

          assert(
            sessionAndTaskOpt match {
              case Some((session, task)) =>
                session.taskId == task.id &&
                  session.userId == user.id &&
                  session.completedAt == None &&
                  session.fromStatus == task.properties.status &&
                  session.toStatus == None &&
                  session.sessionType == taskSessionCreate.sessionType
              case _ => true
            },
            "Task session insert works"
          )
          true
        }
      )
    }
  }

  test("get a task session by id") {
    check {
      forAll(
        (
            userCreate: User.Create,
            annotationProjectCreate: AnnotationProject.Create,
            taskFeaturesCreate: Task.TaskFeatureCollectionCreate,
            taskSessionCreate: TaskSession.Create
        ) => {
          val tfcFirstTaskOnly = taskFeaturesCreate.copy(
            features = taskFeaturesCreate.features.take(1)
          )
          val fetchIO = for {
            dbUser <- UserDao.create(userCreate)
            dbAnnotationProj <-
              AnnotationProjectDao
                .insert(
                  annotationProjectCreate.copy(projectId = None),
                  dbUser
                )
            Task.TaskFeatureCollection(_, features) <- TaskDao.insertTasks(
              fixupTaskFeaturesCollection(
                tfcFirstTaskOnly,
                dbAnnotationProj
              ),
              dbUser
            )
            firstTask = features.headOption
            dbTaskSession <- firstTask traverse { task =>
              TaskSessionDao
                .insertTaskSession(
                  taskSessionCreate,
                  dbUser,
                  task.properties.status,
                  task.id
                )
            }
            fetched <- dbTaskSession flatTraverse { session =>
              TaskSessionDao.getTaskSessionById(session.id)
            }
          } yield (dbTaskSession, fetched)

          val (taskSessionInsertedOpt, taskSessionFetchedOpt) =
            fetchIO.transact(xa).unsafeRunSync

          val insertedAndFetchedOpt =
            (taskSessionInsertedOpt, taskSessionFetchedOpt).tupled

          assert(
            insertedAndFetchedOpt match {
              case Some((inserted, fetched)) =>
                inserted == fetched
              case _ => true
            },
            "Task session fetch works"
          )
          true
        }
      )
    }
  }

  test("keep a session alive") {
    check {
      forAll(
        (
            userCreate: User.Create,
            annotationProjectCreate: AnnotationProject.Create,
            taskFeaturesCreate: Task.TaskFeatureCollectionCreate,
            taskSessionCreate: TaskSession.Create
        ) => {
          val tfcFirstTaskOnly = taskFeaturesCreate.copy(
            features = taskFeaturesCreate.features.take(1)
          )
          val aliveIO = for {
            dbUser <- UserDao.create(userCreate)
            dbAnnotationProj <-
              AnnotationProjectDao
                .insert(
                  annotationProjectCreate.copy(projectId = None),
                  dbUser
                )
            Task.TaskFeatureCollection(_, features) <- TaskDao.insertTasks(
              fixupTaskFeaturesCollection(
                tfcFirstTaskOnly,
                dbAnnotationProj
              ),
              dbUser
            )
            firstTask = features.headOption
            dbTaskSessionId <- firstTask traverse { task =>
              customTaskSessionIO(
                taskSessionCreate,
                dbUser,
                task.properties.status,
                task.id
              )
            }
            almostDead <- dbTaskSessionId flatTraverse { sessionId =>
              TaskSessionDao.getTaskSessionById(sessionId)
            }
            _ <- dbTaskSessionId traverse { sessionId =>
              TaskSessionDao.keepTaskSessionAlive(sessionId)
            }
            reborn <- dbTaskSessionId flatTraverse { sessionId =>
              TaskSessionDao.getTaskSessionById(sessionId)
            }
          } yield (almostDead, reborn)

          val (almostDeadSession, rebornSession) =
            aliveIO.transact(xa).unsafeRunSync

          val eternalityOpt =
            (almostDeadSession, rebornSession).tupled

          assert(
            eternalityOpt match {
              case Some((dead, alive)) =>
                dead.lastTickAt != alive.lastTickAt
              case _ => true
            },
            "Task session can be kept alive"
          )
          true
        }
      )
    }
  }

  test("complete a task session") {
    check {
      forAll(
        (
            userCreate: User.Create,
            annotationProjectCreate: AnnotationProject.Create,
            taskFeaturesCreate: Task.TaskFeatureCollectionCreate,
            taskSessionCreate: TaskSession.Create,
            taskSessionComplete: TaskSession.Complete
        ) => {
          val tfcFirstTaskOnly = taskFeaturesCreate.copy(
            features = taskFeaturesCreate.features.take(1)
          )
          val completeIO = for {
            dbUser <- UserDao.create(userCreate)
            dbAnnotationProj <-
              AnnotationProjectDao
                .insert(
                  annotationProjectCreate.copy(projectId = None),
                  dbUser
                )
            Task.TaskFeatureCollection(_, features) <- TaskDao.insertTasks(
              fixupTaskFeaturesCollection(
                tfcFirstTaskOnly,
                dbAnnotationProj
              ),
              dbUser
            )
            firstTask = features.headOption
            dbTaskSession <- firstTask traverse { task =>
              TaskSessionDao
                .insertTaskSession(
                  taskSessionCreate,
                  dbUser,
                  task.properties.status,
                  task.id
                )
            }
            _ <- dbTaskSession traverse { session =>
              TaskSessionDao.completeTaskSession(
                session.id,
                taskSessionComplete
              )
            }
            completed <- dbTaskSession flatTraverse { session =>
              TaskSessionDao.getTaskSessionById(session.id)
            }
          } yield completed

          val completedSessionOpt = completeIO.transact(xa).unsafeRunSync

          assert(
            completedSessionOpt match {
              case Some(completedSession) =>
                completedSession.completedAt != None &&
                  completedSession.toStatus == Some(
                    taskSessionComplete.toStatus
                  ) &&
                  completedSession.note == taskSessionComplete.note.map(
                    _.toString
                  )
              case _ => true
            },
            "Task session can be kept alive"
          )
          true
        }
      )
    }
  }

  test("check active session") {
    check {
      forAll(
        (
            userCreate: User.Create,
            annotationProjectCreate: AnnotationProject.Create,
            taskFeaturesCreate: Task.TaskFeatureCollectionCreate,
            taskSessionCreate: TaskSession.Create,
            taskSessionComplete: TaskSession.Complete
        ) => {
          val tfcFirstTaskOnly = taskFeaturesCreate.copy(
            features = taskFeaturesCreate.features.take(1)
          )
          val activeIO = for {
            dbUser <- UserDao.create(userCreate)
            dbAnnotationProj <-
              AnnotationProjectDao
                .insert(
                  annotationProjectCreate.copy(projectId = None),
                  dbUser
                )
            Task.TaskFeatureCollection(_, features) <- TaskDao.insertTasks(
              fixupTaskFeaturesCollection(
                tfcFirstTaskOnly,
                dbAnnotationProj
              ),
              dbUser
            )
            firstTask = features.headOption
            dbTaskSessionId <- firstTask traverse { task =>
              customTaskSessionIO(
                taskSessionCreate,
                dbUser,
                task.properties.status,
                task.id
              )
            }
            // the custom session's last tick was 10 minutes ago, it is not an active one anymore
            isActiveSessionOne <- firstTask traverse { task =>
              TaskSessionDao.hasActiveSessionByTaskId(task.id)
            }
            // now we update the session's last tick at by keeping it alive
            _ <- dbTaskSessionId traverse { sessionId =>
              TaskSessionDao.keepTaskSessionAlive(sessionId)
            }
            // the session now should have a more recent last tick at, and it is not complete yet
            isActiveSessionTwo <- firstTask traverse { task =>
              TaskSessionDao.hasActiveSessionByTaskId(task.id)
            }
            // now we complete the session
            _ <- dbTaskSessionId traverse { sessionId =>
              TaskSessionDao.completeTaskSession(
                sessionId,
                taskSessionComplete
              )
            }
            // the session now should be complete
            isActiveSessionThree <- firstTask traverse { task =>
              TaskSessionDao.hasActiveSessionByTaskId(task.id)
            }
          } yield (isActiveSessionOne, isActiveSessionTwo, isActiveSessionThree)

          val (
            isActiveSessionOneOpt,
            isActiveSessionTwoOpt,
            isActiveSessionThreeOpt
          ) =
            activeIO.transact(xa).unsafeRunSync

          val tuped = (
            isActiveSessionOneOpt,
            isActiveSessionTwoOpt,
            isActiveSessionThreeOpt
          ).tupled

          assert(
            tuped match {
              case Some((one, two, three)) =>
                one == false && two == true && three == false
              case _ => true
            },
            "Task session can be kept alive"
          )
          true
        }
      )
    }
  }

}
