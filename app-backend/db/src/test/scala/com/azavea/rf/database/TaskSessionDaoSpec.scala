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

  test("list sessions by task") {
    check {
      forAll(
        (
            userCreate: User.Create,
            annotationProjectCreate: AnnotationProject.Create,
            taskFeaturesCreate: Task.TaskFeatureCollectionCreate,
            taskSessionCreate: TaskSession.Create
        ) => {
          val insertIO = for {
            dbUser <- UserDao.create(userCreate)
            dbAnnotationProj <-
              AnnotationProjectDao
                .insert(
                  annotationProjectCreate.copy(projectId = None),
                  dbUser
                )
            fixedUpTasks = fixupTaskFeaturesCollection(
              taskFeaturesCreate,
              dbAnnotationProj,
              None
            )
            task <- TaskDao.insertTasks(
              fixedUpTasks.copy(features = fixedUpTasks.features.take(1)),
              dbUser
            ) map { _.features.head }
            dbTaskSessionOne <-
              TaskSessionDao
                .insertTaskSession(
                  taskSessionCreate,
                  dbUser,
                  task.properties.status,
                  task.id
                )
            dbTaskSessionTwo <-
              TaskSessionDao
                .insertTaskSession(
                  taskSessionCreate,
                  dbUser,
                  task.properties.status,
                  task.id
                )
            listedSessionTwo <- TaskSessionDao.listSessionsByTask(
              task.id,
              Some(List(dbTaskSessionOne.id))
            )
            listedSessionNone <- TaskSessionDao.listSessionsByTask(
              task.id,
              Some(List(dbTaskSessionOne.id, dbTaskSessionTwo.id))
            )
          } yield (dbTaskSessionTwo, listedSessionTwo, listedSessionNone)

          val (dbSessionTwo, listedSecondSession, listedNoSession) =
            insertIO.transact(xa).unsafeRunSync

          assert(
            Set(dbSessionTwo) == listedSecondSession.toSet,
            "List session by task ID works with session exclussions"
          )
          assert(
            listedNoSession.size == 0,
            "No session listed for task if all sessions are excluded"
          )
          true
        }
      )
    }
  }

  test("Insert labels to session, toggle label, list label") {
    check {
      forAll {
        (
            userCreate: User.Create,
            annotationProjectCreate: AnnotationProject.Create,
            taskFeaturesCreate: Task.TaskFeatureCollectionCreate,
            taskSessionCreate: TaskSession.Create,
            annotationCreates: List[AnnotationLabelWithClasses.Create]
        ) =>
          {
            val labelsOpt = annotationCreates.toNel
            val listIO = for {
              dbUser <- UserDao.create(userCreate)
              dbAnnotationProj <-
                AnnotationProjectDao
                  .insert(
                    annotationProjectCreate.copy(
                      projectId = None,
                      labelClassGroups =
                        annotationProjectCreate.labelClassGroups.take(1)
                    ),
                    dbUser
                  )
              classIds = dbAnnotationProj.labelClassGroups flatMap {
                _.labelClasses
              } map { _.id }
              fixedUpTasks = fixupTaskFeaturesCollection(
                taskFeaturesCreate,
                dbAnnotationProj,
                None
              )
              task <- TaskDao.insertTasks(
                fixedUpTasks.copy(features = fixedUpTasks.features.take(1)),
                dbUser
              ) map { _.features.head }
              dbTaskSession <-
                TaskSessionDao
                  .insertTaskSession(
                    taskSessionCreate,
                    dbUser,
                    task.properties.status,
                    task.id
                  )
              insertedLabelsOpt <- labelsOpt traverse { labels =>
                TaskSessionDao.insertLabels(
                  task.id,
                  dbTaskSession.id,
                  labels.toList map { create =>
                    addClasses(create, classIds)
                      .copy(sessionId = Some(dbTaskSession.id))
                  },
                  dbUser
                )
              }
              labelToUpdateOpt = insertedLabelsOpt flatMap (_.headOption)
              _ <- labelToUpdateOpt traverse { labelToUpdate =>
                AnnotationLabelDao.toggleByLabelId(labelToUpdate.id, false)
              }
              listedLabels <- TaskSessionDao.listActiveLabels(dbTaskSession.id)
              _ <- labelToUpdateOpt traverse { labelToUpdate =>
                AnnotationLabelDao.toggleByLabelId(labelToUpdate.id, true)
              }
              listedLabelsAfterToggle <-
                TaskSessionDao.listActiveLabels(dbTaskSession.id)
            } yield (insertedLabelsOpt, listedLabels, listedLabelsAfterToggle)

            val (insertedO, listed, listedToggled) =
              listIO.transact(xa).unsafeRunSync

            assert(
              insertedO match {
                case Some(inserted) =>
                  inserted.size == listed.size + 1 && inserted.size == listedToggled.size
                case _ => true
              },
              "Label insert and toggle both work and listing only includes active labels"
            )

            true
          }
      }
    }
  }

  test(
    "update label in the current session and from another session of the same task"
  ) {
    check {
      forAll {
        (
            userCreate: User.Create,
            annotationProjectCreate: AnnotationProject.Create,
            taskFeaturesCreate: Task.TaskFeatureCollectionCreate,
            taskSessionCreate: TaskSession.Create,
            labelCreate: AnnotationLabelWithClasses.Create
        ) =>
          {
            val updateIO = for {
              dbUser <- UserDao.create(userCreate)
              dbAnnotationProj <-
                AnnotationProjectDao
                  .insert(
                    annotationProjectCreate.copy(
                      projectId = None,
                      labelClassGroups =
                        annotationProjectCreate.labelClassGroups.take(1)
                    ),
                    dbUser
                  )
              classIds = dbAnnotationProj.labelClassGroups flatMap {
                _.labelClasses
              } map { _.id }
              fixedUpTasks = fixupTaskFeaturesCollection(
                taskFeaturesCreate,
                dbAnnotationProj,
                None
              )
              task <- TaskDao.insertTasks(
                fixedUpTasks.copy(features = fixedUpTasks.features.take(1)),
                dbUser
              ) map { _.features.head }
              dbTaskSessionOne <-
                TaskSessionDao
                  .insertTaskSession(
                    taskSessionCreate,
                    dbUser,
                    task.properties.status,
                    task.id
                  )
              dbTaskSessionTwo <-
                TaskSessionDao
                  .insertTaskSession(
                    taskSessionCreate,
                    dbUser,
                    task.properties.status,
                    task.id
                  )
              insertedLabels <- TaskSessionDao.insertLabels(
                task.id,
                dbTaskSessionOne.id,
                List(
                  addClasses(labelCreate, classIds)
                    .copy(sessionId = Some(dbTaskSessionOne.id))
                ),
                dbUser
              )
              newClassIds = classIds.take(1)
              // updating label from session one with updated class ID list
              updatedLabelOpt <- insertedLabels.headOption flatTraverse {
                inserted =>
                  TaskSessionDao.updateLabel(
                    task.id,
                    dbTaskSessionOne.id,
                    inserted.id,
                    inserted.copy(annotationLabelClasses = newClassIds),
                    dbUser
                  )
              }
              _ <- TaskSessionDao.completeTaskSession(
                dbTaskSessionOne.id,
                TaskSession.Complete(TaskStatus.Labeled, None)
              )
              // update label from session one by submitting it to session two
              // label is from the same task but from session one
              // so label from session one will be deactivated
              // label one will be added as a label in session two
              updatedLabelToSessionTwo <-
                updatedLabelOpt.headOption flatTraverse { updated =>
                  TaskSessionDao.updateLabel(
                    task.id,
                    dbTaskSessionTwo.id,
                    updated.id,
                    updated,
                    dbUser
                  )
                }
              // all labels from session one should be deactivated
              listedFromSessionOne <-
                TaskSessionDao.listActiveLabels(dbTaskSessionOne.id)
              listedFromSessionTwo <-
                TaskSessionDao.listActiveLabels(dbTaskSessionTwo.id)
            } yield (
              updatedLabelOpt,
              updatedLabelToSessionTwo,
              listedFromSessionOne,
              listedFromSessionTwo,
              newClassIds,
              dbTaskSessionTwo
            )

            val (
              updatedLabel,
              updatedLabelInSessionTwo,
              listedSessionOne,
              listedSessionTwo,
              newIds,
              sessionTwo
            ) = updateIO.transact(xa).unsafeRunSync

            assert(
              updatedLabel.foldLeft(true)((acc, label) =>
                label.annotationLabelClasses.toSet == newIds.toSet && acc
              ),
              "Updated label one has new classes"
            )
            assert(
              (updatedLabel, updatedLabelInSessionTwo).tupled.foldLeft(true)(
                (acc, labels) => {
                  val (updated, updatedInTwo) = labels
                  updated.description == updatedInTwo.description && updatedInTwo.annotationLabelClasses.toSet == newIds.toSet && Some(
                    sessionTwo.id
                  ) == updatedInTwo.sessionId && acc
                }
              ),
              "Updated label two has the same content as the updated label one"
            )

            assert(
              listedSessionOne.size == 0,
              "After using label one to update label two, label one is deactivated"
            )

            assert(
              listedSessionTwo.size == 1,
              "After using label one to update label two, label one is deactivated"
            )

            true
          }
      }
    }
  }

  test("Delete labels in different sessions of the same task") {
    check {
      forAll {
        (
            userCreate: User.Create,
            annotationProjectCreate: AnnotationProject.Create,
            taskFeaturesCreate: Task.TaskFeatureCollectionCreate,
            taskSessionCreate: TaskSession.Create,
            labelCreate: AnnotationLabelWithClasses.Create
        ) =>
          {
            val deleteIO = for {
              dbUser <- UserDao.create(userCreate)
              dbAnnotationProj <-
                AnnotationProjectDao
                  .insert(
                    annotationProjectCreate.copy(
                      projectId = None,
                      labelClassGroups =
                        annotationProjectCreate.labelClassGroups.take(1)
                    ),
                    dbUser
                  )
              classIds = dbAnnotationProj.labelClassGroups flatMap {
                _.labelClasses
              } map { _.id }
              fixedUpTasks = fixupTaskFeaturesCollection(
                taskFeaturesCreate,
                dbAnnotationProj,
                None
              )
              task <- TaskDao.insertTasks(
                fixedUpTasks.copy(features = fixedUpTasks.features.take(1)),
                dbUser
              ) map { _.features.head }
              dbTaskSessionOne <-
                TaskSessionDao
                  .insertTaskSession(
                    taskSessionCreate,
                    dbUser,
                    task.properties.status,
                    task.id
                  )
              insertedLabelsOne <- TaskSessionDao.insertLabels(
                task.id,
                dbTaskSessionOne.id,
                List(
                  addClasses(labelCreate, classIds)
                    .copy(sessionId = Some(dbTaskSessionOne.id))
                ),
                dbUser
              )
              insertedLabelsTwo <- TaskSessionDao.insertLabels(
                task.id,
                dbTaskSessionOne.id,
                List(
                  addClasses(labelCreate, classIds)
                    .copy(sessionId = Some(dbTaskSessionOne.id))
                ),
                dbUser
              )
              // delete the first label from session one
              // then get the label from the DB
              // label in the same session should be just removed
              deleteLabelOneOpt <- insertedLabelsOne.headOption flatTraverse {
                labelOne =>
                  TaskSessionDao.deleteLabel(
                    task.id,
                    dbTaskSessionOne.id,
                    labelOne.id
                  ) *> TaskSessionDao.getLabel(dbTaskSessionOne.id, labelOne.id)
              }
              _ <- TaskSessionDao.completeTaskSession(
                dbTaskSessionOne.id,
                TaskSession.Complete(TaskStatus.Labeled, None)
              )
              dbTaskSessionTwo <-
                TaskSessionDao
                  .insertTaskSession(
                    taskSessionCreate,
                    dbUser,
                    task.properties.status,
                    task.id
                  )
              // delete label 2 from session 2
              // the label is deactivated
              // you can still access the label
              // but listing active labels from session 1 will not include it
              softDeletedLabelTwo <- insertedLabelsTwo.headOption flatTraverse {
                labelTwo =>
                  TaskSessionDao.deleteLabel(
                    task.id,
                    dbTaskSessionTwo.id,
                    labelTwo.id
                  ) *> TaskSessionDao.getLabel(dbTaskSessionOne.id, labelTwo.id)
              }
              sessionOneActiveLabels <-
                TaskSessionDao.listActiveLabels(dbTaskSessionOne.id)
            } yield (
              deleteLabelOneOpt,
              insertedLabelsTwo,
              softDeletedLabelTwo,
              sessionOneActiveLabels
            )

            val (
              deletedOne,
              insertedTwo,
              softDeletedTwo,
              activeLabelsFromSessionOne
            ) = deleteIO.transact(xa).unsafeRunSync

            assert(
              deletedOne == None,
              "Label deleted in the session it is created in is a hard delete"
            )
            assert(
              (insertedTwo.headOption, softDeletedTwo).tupled.foldLeft(true)(
                (acc, (labels)) => {
                  val (inserted, softDeleted) = labels
                  acc && inserted.description == softDeleted.description && inserted.isActive == true && softDeleted.isActive == false
                }
              ),
              "Labeled deleted in the session it is not created is a soft delete"
            )

            assert(
              activeLabelsFromSessionOne.size == 0,
              "No labels left in session one after hard and soft deletions"
            )

            true
          }
      }
    }
  }

}
