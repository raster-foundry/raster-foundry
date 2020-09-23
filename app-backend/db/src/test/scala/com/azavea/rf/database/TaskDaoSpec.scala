package com.rasterfoundry.database

import com.rasterfoundry.common.Generators.Implicits._
import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.datamodel.GeoJsonCodec.PaginatedGeoJsonResponse
import com.rasterfoundry.datamodel._

import cats.data.NonEmptyList
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._
import eu.timepit.refined.refineMV
import eu.timepit.refined.auto._
import eu.timepit.refined.types.string.NonEmptyString
import monocle.Lens
import monocle.macros.GenLens
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.Checkers

import scala.concurrent.duration._

class TaskDaoSpec
    extends AnyFunSuite
    with Matchers
    with Checkers
    with DBTestConfig
    with LazyLogging
    with PropTestHelpers {
  test("listing some features") {
    check {
      forAll {
        (
            userCreate: User.Create,
            orgCreate: Organization.Create,
            platform: Platform,
            projectCreate: Project.Create,
            taskFeaturesCreate: Task.TaskFeatureCollectionCreate,
            annotationProjectCreate: AnnotationProject.Create
        ) =>
          {
            val connIO: ConnectionIO[
              (
                  Task.TaskFeatureCollection,
                  GeoJsonCodec.PaginatedGeoJsonResponse[Task.TaskFeature]
              )
            ] =
              for {
                (dbUser, _, _, dbProject) <- insertUserOrgPlatProject(
                  userCreate,
                  orgCreate,
                  platform,
                  projectCreate
                )
                dbAnnotationProj <- AnnotationProjectDao
                  .insert(
                    annotationProjectCreate.copy(
                      projectId = Some(dbProject.id)
                    ),
                    dbUser
                  )
                collection <- TaskDao.insertTasks(
                  fixupTaskFeaturesCollection(
                    taskFeaturesCreate,
                    dbAnnotationProj
                  ),
                  dbUser
                )
                fetched <- TaskDao.listTasks(
                  TaskQueryParameters(),
                  dbAnnotationProj.id,
                  PageRequest(0, 10, Map.empty)
                )
              } yield { (collection, fetched) }

            val (featureCollection, fetched) = connIO.transact(xa).unsafeRunSync
            assert(
              (featureCollection.features.toSet & fetched.features.toSet) == fetched.features.toSet,
              "Retrieved and inserted features should be the same"
            )
            true
          }
      }
    }
  }
  test("insert tasks from a task feature collection") {
    check {
      forAll {
        (
            userCreate: User.Create,
            orgCreate: Organization.Create,
            platform: Platform,
            projectCreate: Project.Create,
            taskFeaturesCreate: Task.TaskFeatureCollectionCreate,
            annotationProjectCreate: AnnotationProject.Create
        ) =>
          {
            val connIO: ConnectionIO[
              (Task.TaskFeatureCollection, List[Task], AnnotationProject)
            ] =
              for {
                (dbUser, _, _, dbProject) <- insertUserOrgPlatProject(
                  userCreate,
                  orgCreate,
                  platform,
                  projectCreate
                )
                dbAnnotationProj <- AnnotationProjectDao
                  .insert(
                    annotationProjectCreate.copy(
                      projectId = Some(dbProject.id)
                    ),
                    dbUser
                  )
                collection <- TaskDao.insertTasks(
                  fixupTaskFeaturesCollection(
                    taskFeaturesCreate,
                    dbAnnotationProj
                  ),
                  dbUser
                )
                fetched <- collection.features traverse { feat =>
                  TaskDao.unsafeGetTaskById(feat.id)
                }
                annoProj <- AnnotationProjectDao
                  .unsafeGetById(dbAnnotationProj.id)
              } yield { (collection, fetched, annoProj) }

            val (featureCollection, fetched, annotationProject) =
              connIO.transact(xa).unsafeRunSync

            val projectTaskSummaryCount = annotationProject.taskStatusSummary flatMap {
              summary =>
                Some(summary.valuesIterator.foldLeft(0)(_ + _))
            }

            assert(
              projectTaskSummaryCount == Some(featureCollection.features.size),
              "Task insert operation should update task status summary in annotation project"
            )

            assert(
              featureCollection.features.toSet == fetched
                .map(_.toGeoJSONFeature(Nil))
                .toSet,
              "Retrieved and inserted features should be the same"
            )
            true
          }
      }
    }
  }

  test("insert tasks from a set of grid parameters") {
    check {
      forAll {
        (
            userCreate: User.Create,
            orgCreate: Organization.Create,
            platform: Platform,
            projectCreate: Project.Create,
            maybeSceneData: Option[(Datasource.Create, Scene.Create)],
            taskPropertiesCreate: Task.TaskPropertiesCreate,
            taskGridFeatureCreate: Task.TaskGridFeatureCreate,
            annotationProjectCreate: AnnotationProject.Create
        ) =>
          {
            val connIO: ConnectionIO[
              (
                  Option[Scene.WithRelated],
                  com.rasterfoundry.datamodel.Task.TaskGridFeatureCreate,
                  Int,
                  com.rasterfoundry.datamodel.AnnotationProject.WithRelated
              )
            ] =
              for {
                (dbUser, _, _, dbProject) <- insertUserOrgPlatProject(
                  userCreate,
                  orgCreate,
                  platform,
                  projectCreate
                )
                dbAnnotationProj <- AnnotationProjectDao
                  .insert(
                    annotationProjectCreate.copy(
                      projectId = Some(dbProject.id)
                    ),
                    dbUser
                  )
                createdScene <- maybeSceneData traverse {
                  case (datasourceCreate, sceneCreate) =>
                    for {
                      ds <- fixupDatasource(datasourceCreate, dbUser)
                      created <- SceneDao.insert(
                        fixupSceneCreate(dbUser, ds, sceneCreate),
                        dbUser
                      )
                      _ <- ProjectDao.addScenesToProject(
                        NonEmptyList(created.id, Nil),
                        dbProject.id,
                        dbProject.defaultLayerId,
                        true
                      )
                    } yield created
                }
                taskCount <- TaskDao.insertTasksByGrid(
                  fixupTaskPropertiesCreate(
                    taskPropertiesCreate,
                    dbAnnotationProj
                  ),
                  taskGridFeatureCreate,
                  dbUser
                )
              } yield {
                (
                  createdScene,
                  taskGridFeatureCreate,
                  taskCount,
                  dbAnnotationProj
                )
              }

            val (createdScene, gridFeatures, taskCount, annotationProject) =
              connIO.transact(xa).unsafeRunSync

            (createdScene, gridFeatures.geometry, annotationProject.aoi) match {
              case (_, Some(_), _) | (Some(_), None, _) | (_, _, Some(_)) =>
                assert(
                  taskCount > 0,
                  "Task grid generation resulted in at least one inserted task"
                )
              case _ =>
                assert(
                  taskCount == 0,
                  "Task grid created should not occur without a geometry"
                )
            }
            true
          }
      }
    }
  }

  test("geoJSON selection should work") {
    check {
      forAll {
        (
            userCreate: User.Create,
            orgCreate: Organization.Create,
            platform: Platform,
            projectCreate: Project.Create,
            taskFeaturesCreate: Task.TaskFeatureCollectionCreate,
            annotationProjectCreate: AnnotationProject.Create
        ) =>
          {
            val connIO: ConnectionIO[Boolean] =
              for {
                (dbUser, _, _, dbProject) <- insertUserOrgPlatProject(
                  userCreate,
                  orgCreate,
                  platform,
                  projectCreate
                )
                dbAnnotationProj <- AnnotationProjectDao
                  .insert(
                    annotationProjectCreate.copy(
                      projectId = Some(dbProject.id)
                    ),
                    dbUser
                  )
                collection <- TaskDao.insertTasks(
                  fixupTaskFeaturesCollection(
                    taskFeaturesCreate,
                    dbAnnotationProj
                  ),
                  dbUser
                )
                _ <- TaskDao.getTaskWithActions(collection.features.head.id)
              } yield true

            connIO.transact(xa).unsafeRunSync
          }
      }
    }
  }

  test(
    "updating should append actions when statuses are different and delete should work"
  ) {
    check {
      forAll {
        (
            userCreate: User.Create,
            orgCreate: Organization.Create,
            platform: Platform,
            projectCreate: Project.Create,
            taskFeaturesCreate: Task.TaskFeatureCollectionCreate,
            taskFeatureCreate: Task.TaskFeatureCreate,
            annotationProjectCreate: AnnotationProject.Create
        ) =>
          {
            val connIO = for {
              (dbUser, _, _, dbProject) <- insertUserOrgPlatProject(
                userCreate,
                orgCreate,
                platform,
                projectCreate
              )
              dbAnnotationProj <- AnnotationProjectDao
                .insert(
                  annotationProjectCreate.copy(projectId = Some(dbProject.id)),
                  dbUser
                )
              collection <- TaskDao.insertTasks(
                fixupTaskFeaturesCollection(
                  taskFeaturesCreate,
                  dbAnnotationProj,
                  Some(TaskStatus.Unlabeled)
                ),
                dbUser
              )
              update <- TaskDao.updateTask(
                collection.features.head.id,
                fixupTaskFeatureCreate(
                  taskFeatureCreate,
                  dbAnnotationProj,
                  Some(TaskStatus.Labeled)
                ),
                dbUser
              )
              annoProjAfterUpdate <- AnnotationProjectDao
                .unsafeGetById(dbAnnotationProj.id)
              // have to delete actions on the task to be able to delete it
              _ <- fr"TRUNCATE TABLE task_actions;".update.run
              delete <- TaskDao.deleteTask(collection.features.head.id)
              annoProjAfterDelete <- AnnotationProjectDao
                .unsafeGetById(dbAnnotationProj.id)
              _ <- TaskDao.query
                .filter(fr"annotation_project_id = ${dbAnnotationProj.id}")
                .delete
              annoProjAfterDrop <- AnnotationProjectDao.unsafeGetById(
                dbAnnotationProj.id
              )
            } yield
              (
                update,
                delete,
                annoProjAfterUpdate,
                collection.features.size,
                annoProjAfterDelete,
                annoProjAfterDrop
              )

            val (
              updateResult,
              deleteResult,
              annoProjAfterUpd,
              taskOriginalCount,
              annoProjAfterDel,
              annoProjAfterDropAll
            ) = connIO.transact(xa).unsafeRunSync

            updateResult.get.properties.actions.length should be(1)

            val unlabeledCountAfterUpdate = annoProjAfterUpd.taskStatusSummary
              .flatMap(_.get(TaskStatus.Unlabeled.toString))
            val labeledCountAfterUpdate = annoProjAfterUpd.taskStatusSummary
              .flatMap(_.get(TaskStatus.Labeled.toString))
            val unlabeledCountAfterDelete = annoProjAfterDel.taskStatusSummary
              .flatMap(_.get(TaskStatus.Unlabeled.toString))
            val labeledCountAfterDelete = annoProjAfterDel.taskStatusSummary
              .flatMap(_.get(TaskStatus.Labeled.toString))
            val taskCountAfterDropAll = annoProjAfterDropAll.taskStatusSummary flatMap {
              summary =>
                Some(summary.valuesIterator.foldLeft(0)(_ + _))
            }

            assert(
              unlabeledCountAfterUpdate == Some(taskOriginalCount - 1),
              "For unlabeled, task update should update task status summary in annotation project"
            )
            assert(
              labeledCountAfterUpdate == Some(1),
              "For labeled, task update should update task status summary in annotation project"
            )
            assert(
              unlabeledCountAfterDelete == Some(taskOriginalCount - 1),
              "For unlabeled, task delete should update task status summary in annotation project"
            )
            assert(
              labeledCountAfterDelete == Some(0),
              "For labeled, task delete should update task status summary in annotation project"
            )
            assert(
              taskCountAfterDropAll == Some(0),
              "Task delete all should update task status summary in annotation project"
            )
            assert(
              updateResult.get.properties.taskType == taskFeatureCreate.properties.taskType
                .getOrElse(
                  taskFeaturesCreate.features.head.properties.taskType
                    .getOrElse(TaskType.Label)
                ),
              "taskType is updated correctly -- and so other fields should be too"
            )
            deleteResult should be(1)
            true
          }
      }
    }
  }

  test("shouldn't list duplicates if an action is in two columns") {
    check {
      forAll {
        (
            userCreate: User.Create,
            orgCreate: Organization.Create,
            platform: Platform,
            projectCreate: Project.Create,
            taskFeatureCreate: Task.TaskFeatureCreate,
            annotationProjectCreate: AnnotationProject.Create
        ) =>
          {
            val connIO: ConnectionIO[
              (
                  Task.TaskFeatureCollection,
                  PaginatedGeoJsonResponse[Task.TaskFeature]
              )
            ] =
              for {
                (dbUser, _, _, dbProject) <- insertUserOrgPlatProject(
                  userCreate,
                  orgCreate,
                  platform,
                  projectCreate
                )
                dbAnnotationProj <- AnnotationProjectDao
                  .insert(
                    annotationProjectCreate.copy(
                      projectId = Some(dbProject.id)
                    ),
                    dbUser
                  )
                collection <- TaskDao.insertTasks(
                  Task.TaskFeatureCollectionCreate(
                    features = List(
                      fixupTaskFeatureCreate(
                        taskFeatureCreate,
                        dbAnnotationProj
                      ).withStatus(TaskStatus.Unlabeled)
                    )
                  ),
                  dbUser
                )
                feature = collection.features.head
                newStatus = TaskStatus.Labeled
                _ <- TaskDao.updateTask(
                  feature.id,
                  Task.TaskFeatureCreate(
                    feature.properties.copy(status = newStatus).toCreate,
                    feature.geometry
                  ),
                  dbUser
                )
                _ <- TaskDao.updateTask(
                  feature.id,
                  Task.TaskFeatureCreate(
                    feature.properties.toCreate,
                    feature.geometry
                  ),
                  dbUser
                )
                listed <- TaskDao.listTasks(
                  TaskQueryParameters(actionType = Some(TaskStatus.Unlabeled)),
                  dbAnnotationProj.id,
                  PageRequest(0, 10, Map.empty)
                )
              } yield { (collection, listed) }

            val (inserted, listed) = connIO.transact(xa).unsafeRunSync

            assert(
              listed.features.toList
                .filter(_.id == inserted.features.head.id)
                .length == 1,
              "shouldn't have duplicates"
            )
            true
          }
      }
    }
  }

  test(
    "locking and unlocking update tasks appropriately"
  ) {
    check {
      forAll {
        (
            userCreate: User.Create,
            orgCreate: Organization.Create,
            platform: Platform,
            projectCreate: Project.Create,
            taskFeatureCreate: Task.TaskFeatureCreate,
            annotationProjectCreate: AnnotationProject.Create
        ) =>
          {
            val connIO = for {
              (dbUser, _, _, dbProject) <- insertUserOrgPlatProject(
                userCreate,
                orgCreate,
                platform,
                projectCreate
              )
              dbAnnotationProj <- AnnotationProjectDao
                .insert(
                  annotationProjectCreate.copy(projectId = Some(dbProject.id)),
                  dbUser
                )
              collection <- TaskDao.insertTasks(
                Task.TaskFeatureCollectionCreate(
                  features = List(
                    fixupTaskFeatureCreate(
                      taskFeatureCreate,
                      dbAnnotationProj
                    ).withStatus(TaskStatus.Unlabeled)
                  )
                ),
                dbUser
              )
              dbUser2 <- UserDao.create(User.Create("a different user"))
              feature = collection.features.head
              // No one has locked this task yet, so this should be `true`
              authCheck1 <- TaskDao.isLockingUserOrUnlocked(feature.id, dbUser)
              locked <- TaskDao.lockTask(feature.id)(dbUser)
              // This user just locked this task, so it should still be `true`
              authCheck2 <- TaskDao.isLockingUserOrUnlocked(feature.id, dbUser)
              // but dbUser2 didn't lock it, so this should be `false`
              authCheck3 <- TaskDao.isLockingUserOrUnlocked(feature.id, dbUser2)
              unlocked <- TaskDao.unlockTask(feature.id)
              // We unlocked it, so this should also be `true`
              authCheck4 <- TaskDao.isLockingUserOrUnlocked(feature.id, dbUser)
            } yield {
              (
                authCheck1,
                authCheck2,
                authCheck3,
                authCheck4,
                locked,
                unlocked,
                dbUser
              )
            }

            val (check1, check2, check3, check4, locked, unlocked, user) =
              connIO.transact(xa).unsafeRunSync

            // Check auth results
            check1 should be(true)
            check2 should be(true)
            check3 should be(false)
            check4 should be(true)

            locked.get.properties.lockedBy should be(Some(user.id))
            unlocked.get.properties.lockedBy should be(None)

            true
          }
      }
    }

  }

  test("delete all tasks in a project") {
    check {
      forAll {
        (
            userCreate: User.Create,
            orgCreate: Organization.Create,
            platform: Platform,
            projectCreate: Project.Create,
            taskFeaturesCreate: Task.TaskFeatureCollectionCreate,
            annotationProjectCreate: AnnotationProject.Create
        ) =>
          {
            val fetchedAndDeletedIO =
              for {
                (dbUser, _, _, dbProject) <- insertUserOrgPlatProject(
                  userCreate,
                  orgCreate,
                  platform,
                  projectCreate
                )
                dbAnnotationProj <- AnnotationProjectDao
                  .insert(
                    annotationProjectCreate.copy(
                      projectId = Some(dbProject.id)
                    ),
                    dbUser
                  )
                _ <- TaskDao.insertTasks(
                  fixupTaskFeaturesCollection(
                    taskFeaturesCreate,
                    dbAnnotationProj
                  ),
                  dbUser
                )
                fetched <- TaskDao.listTasks(
                  TaskQueryParameters(),
                  dbAnnotationProj.id,
                  PageRequest(0, 10, Map.empty)
                )
                deletedRowCount <- TaskDao.deleteProjectTasks(
                  dbAnnotationProj.id
                )
              } yield { (fetched, deletedRowCount) }

            val (tasks, deletedtaskCount) =
              fetchedAndDeletedIO.transact(xa).unsafeRunSync
            assert(
              tasks.count == deletedtaskCount,
              "Retrieved and deleted tasks should be the same"
            )
            true
          }
      }
    }
  }

  test("list user actions on tasks with label and validate performed") {
    check {
      forAll {
        (
            userCreate: User.Create,
            orgCreate: Organization.Create,
            platform: Platform,
            projCreate: (Project.Create, AnnotationProject.Create),
            taskFeatureCreate: Task.TaskFeatureCreate,
            labelValidateTeamCreate: (Team.Create, Team.Create),
            labelValidateTeamUgrCreate: (
                UserGroupRole.Create,
                UserGroupRole.Create
            )
        ) =>
          {
            val (projectCreate, annotationProjectCreate) = projCreate
            val connIO = for {
              (dbUser, dbOrg, dbPlatform, _) <- insertUserOrgPlatProject(
                userCreate,
                orgCreate,
                platform,
                projectCreate
              )
              (labelTeam, validateTeam) <- fixupAssignUserToTeams(
                labelValidateTeamCreate,
                labelValidateTeamUgrCreate,
                dbOrg,
                dbUser,
                dbPlatform
              )
              dbAnnotationProj <- AnnotationProjectDao
                .insert(
                  annotationProjectCreate.copy(
                    labelersTeamId = Some(labelTeam.id),
                    validatorsTeamId = Some(validateTeam.id)
                  ),
                  dbUser
                )
              collection <- TaskDao.insertTasks(
                Task.TaskFeatureCollectionCreate(
                  features = List(
                    fixupTaskFeatureCreate(
                      taskFeatureCreate,
                      dbAnnotationProj
                    ).withStatus(TaskStatus.Unlabeled)
                  )
                ),
                dbUser
              )
              feature = collection.features.head
              _ <- TaskDao.updateTask(
                feature.id,
                Task.TaskFeatureCreate(
                  feature.properties
                    .copy(status = TaskStatus.LabelingInProgress)
                    .toCreate,
                  feature.geometry
                ),
                dbUser
              )
              _ <- TaskDao.updateTask(
                feature.id,
                Task.TaskFeatureCreate(
                  feature.properties.copy(status = TaskStatus.Labeled).toCreate,
                  feature.geometry
                ),
                dbUser
              )
              _ <- TaskDao.updateTask(
                feature.id,
                Task.TaskFeatureCreate(
                  feature.properties
                    .copy(status = TaskStatus.ValidationInProgress)
                    .toCreate,
                  feature.geometry
                ),
                dbUser
              )
              _ <- TaskDao.updateTask(
                feature.id,
                Task.TaskFeatureCreate(
                  feature.properties
                    .copy(status = TaskStatus.Validated)
                    .toCreate,
                  feature.geometry
                ),
                dbUser
              )
              listed <- TaskDao.getTaskUserSummary(
                dbAnnotationProj.id,
                UserTaskActivityParameters()
              )
            } yield { (dbUser, listed) }

            val (user, userTaskSummary) = connIO.transact(xa).unsafeRunSync

            assert(
              userTaskSummary.head.userId == user.id,
              "should match the ID of the action user"
            )
            assert(
              userTaskSummary.head.name == user.name,
              "should match the name of the action user"
            )
            assert(
              userTaskSummary.head.profileImageUri == user.profileImageUri,
              "should match the avatar of the action user"
            )
            assert(
              userTaskSummary.head.labeledTaskCount == 1,
              "action user should have 1 labeled task"
            )
            assert(
              userTaskSummary.head.validatedTaskCount == 1,
              "action user should have 1 validated task"
            )
            true
          }
      }
    }
  }

  test("list user actions on tasks with only label performed") {
    check {
      forAll {
        (
            userCreate: User.Create,
            orgCreate: Organization.Create,
            platform: Platform,
            projCreate: (Project.Create, AnnotationProject.Create),
            taskFeatureCreate: Task.TaskFeatureCreate,
            labelValidateTeamCreate: (Team.Create, Team.Create),
            labelValidateTeamUgrCreate: (
                UserGroupRole.Create,
                UserGroupRole.Create
            )
        ) =>
          {
            val (projectCreate, annotationProjectCreate) = projCreate
            val connIO = for {
              (dbUser, dbOrg, dbPlatform, _) <- insertUserOrgPlatProject(
                userCreate,
                orgCreate,
                platform,
                projectCreate
              )
              (labelTeam, validateTeam) <- fixupAssignUserToTeams(
                labelValidateTeamCreate,
                labelValidateTeamUgrCreate,
                dbOrg,
                dbUser,
                dbPlatform
              )
              dbAnnotationProj <- AnnotationProjectDao
                .insert(
                  annotationProjectCreate.copy(
                    labelersTeamId = Some(labelTeam.id),
                    validatorsTeamId = Some(validateTeam.id)
                  ),
                  dbUser
                )
              collection <- TaskDao.insertTasks(
                Task.TaskFeatureCollectionCreate(
                  features = List(
                    fixupTaskFeatureCreate(
                      taskFeatureCreate,
                      dbAnnotationProj
                    ).withStatus(TaskStatus.Unlabeled)
                  )
                ),
                dbUser
              )
              feature = collection.features.head
              _ <- TaskDao.updateTask(
                feature.id,
                Task.TaskFeatureCreate(
                  feature.properties
                    .copy(status = TaskStatus.LabelingInProgress)
                    .toCreate,
                  feature.geometry
                ),
                dbUser
              )
              _ <- TaskDao.updateTask(
                feature.id,
                Task.TaskFeatureCreate(
                  feature.properties.copy(status = TaskStatus.Labeled).toCreate,
                  feature.geometry
                ),
                dbUser
              )
              listed <- TaskDao.getTaskUserSummary(
                dbAnnotationProj.id,
                UserTaskActivityParameters()
              )
            } yield { (dbUser, listed) }

            val (user, userTaskSummary) = connIO.transact(xa).unsafeRunSync

            assert(
              userTaskSummary.head.userId == user.id,
              "should match the ID of the action user"
            )
            assert(
              userTaskSummary.head.name == user.name,
              "should match the name of the action user"
            )
            assert(
              userTaskSummary.head.profileImageUri == user.profileImageUri,
              "should match the avatar of the action user"
            )
            assert(
              userTaskSummary.head.labeledTaskCount == 1,
              "action user should have 1 labeled task"
            )
            assert(
              userTaskSummary.head.validatedTaskCount == 0,
              "action user should have 1 validated task"
            )
            true
          }
      }
    }
  }

  test("list user actions on tasks with only validate performed") {
    check {
      forAll {
        (
            userCreate: User.Create,
            orgCreate: Organization.Create,
            platform: Platform,
            projCreate: (Project.Create, AnnotationProject.Create),
            taskFeatureCreate: Task.TaskFeatureCreate,
            labelValidateTeamCreate: (Team.Create, Team.Create),
            labelValidateTeamUgrCreate: (
                UserGroupRole.Create,
                UserGroupRole.Create
            )
        ) =>
          {
            val (projectCreate, annotationProjectCreate) = projCreate
            val connIO = for {
              (dbUser, dbOrg, dbPlatform, _) <- insertUserOrgPlatProject(
                userCreate,
                orgCreate,
                platform,
                projectCreate
              )
              (labelTeam, validateTeam) <- fixupAssignUserToTeams(
                labelValidateTeamCreate,
                labelValidateTeamUgrCreate,
                dbOrg,
                dbUser,
                dbPlatform
              )
              dbAnnotationProj <- AnnotationProjectDao
                .insert(
                  annotationProjectCreate.copy(
                    labelersTeamId = Some(labelTeam.id),
                    validatorsTeamId = Some(validateTeam.id)
                  ),
                  dbUser
                )
              collection <- TaskDao.insertTasks(
                Task.TaskFeatureCollectionCreate(
                  features = List(
                    fixupTaskFeatureCreate(
                      taskFeatureCreate,
                      dbAnnotationProj
                    ).withStatus(TaskStatus.Labeled)
                  )
                ),
                dbUser
              )
              feature = collection.features.head
              _ <- TaskDao.updateTask(
                feature.id,
                Task.TaskFeatureCreate(
                  feature.properties
                    .copy(status = TaskStatus.ValidationInProgress)
                    .toCreate,
                  feature.geometry
                ),
                dbUser
              )
              _ <- TaskDao.updateTask(
                feature.id,
                Task.TaskFeatureCreate(
                  feature.properties
                    .copy(status = TaskStatus.Validated)
                    .toCreate,
                  feature.geometry
                ),
                dbUser
              )
              listed <- TaskDao.getTaskUserSummary(
                dbAnnotationProj.id,
                UserTaskActivityParameters()
              )
            } yield { (dbUser, listed) }

            val (user, userTaskSummary) = connIO.transact(xa).unsafeRunSync

            assert(
              userTaskSummary.head.userId == user.id,
              "should match the ID of the action user"
            )
            assert(
              userTaskSummary.head.name == user.name,
              "should match the name of the action user"
            )
            assert(
              userTaskSummary.head.profileImageUri == user.profileImageUri,
              "should match the avatar of the action user"
            )
            assert(
              userTaskSummary.head.labeledTaskCount == 0,
              "action user should have 1 labeled task"
            )
            assert(
              userTaskSummary.head.validatedTaskCount == 1,
              "action user should have 1 validated task"
            )
            true
          }
      }
    }
  }

  test("list user actions on tasks with nothing performed") {
    check {
      forAll {
        (
            userCreate: User.Create,
            orgCreate: Organization.Create,
            platform: Platform,
            projCreate: (Project.Create, AnnotationProject.Create),
            taskFeatureCreate: Task.TaskFeatureCreate,
            labelValidateTeamCreate: (Team.Create, Team.Create),
            labelValidateTeamUgrCreate: (
                UserGroupRole.Create,
                UserGroupRole.Create
            )
        ) =>
          {
            val (projectCreate, annotationProjectCreate) = projCreate
            val connIO = for {
              (dbUser, dbOrg, dbPlatform, _) <- insertUserOrgPlatProject(
                userCreate,
                orgCreate,
                platform,
                projectCreate
              )
              (labelTeam, validateTeam) <- fixupAssignUserToTeams(
                labelValidateTeamCreate,
                labelValidateTeamUgrCreate,
                dbOrg,
                dbUser,
                dbPlatform
              )
              dbAnnotationProj <- AnnotationProjectDao
                .insert(
                  annotationProjectCreate.copy(
                    labelersTeamId = Some(labelTeam.id),
                    validatorsTeamId = Some(validateTeam.id)
                  ),
                  dbUser
                )
              _ <- TaskDao.insertTasks(
                Task.TaskFeatureCollectionCreate(
                  features = List(
                    fixupTaskFeatureCreate(
                      taskFeatureCreate,
                      dbAnnotationProj
                    ).withStatus(TaskStatus.Unlabeled)
                  )
                ),
                dbUser
              )
              listed <- TaskDao.getTaskUserSummary(
                dbAnnotationProj.id,
                UserTaskActivityParameters()
              )
            } yield { (dbUser, listed) }

            val (user, userTaskSummary) = connIO.transact(xa).unsafeRunSync

            assert(
              userTaskSummary.head.userId == user.id,
              "should match the ID of the action user"
            )
            assert(
              userTaskSummary.head.name == user.name,
              "should match the name of the action user"
            )
            assert(
              userTaskSummary.head.profileImageUri == user.profileImageUri,
              "should match the avatar of the action user"
            )
            assert(
              userTaskSummary.head.labeledTaskCount == 0,
              "action user should have 1 labeled task"
            )
            assert(
              userTaskSummary.head.validatedTaskCount == 0,
              "action user should have 1 validated task"
            )
            true
          }
      }
    }
  }

  test("list tasks by a list of statuses") {
    check {
      forAll {
        (
            userCreate: User.Create,
            orgCreate: Organization.Create,
            platform: Platform,
            projCreate: (Project.Create, AnnotationProject.Create),
            taskFeaturesCreateOne: Task.TaskFeatureCollectionCreate,
            taskFeaturesCreateTwo: Task.TaskFeatureCollectionCreate
        ) =>
          {
            val (projectCreate, annotationProjectCreate) = projCreate
            val connIO = for {
              (dbUser, _, _, dbProject) <- insertUserOrgPlatProject(
                userCreate,
                orgCreate,
                platform,
                projectCreate
              )
              dbAnnotationProj <- AnnotationProjectDao
                .insert(
                  annotationProjectCreate.copy(
                    projectId = Some(dbProject.id)
                  ),
                  dbUser
                )
              collectionOne <- TaskDao.insertTasks(
                fixupTaskFeaturesCollection(
                  taskFeaturesCreateOne,
                  dbAnnotationProj,
                  Some(TaskStatus.Labeled)
                ),
                dbUser
              )
              collectionTwo <- TaskDao.insertTasks(
                fixupTaskFeaturesCollection(
                  taskFeaturesCreateTwo,
                  dbAnnotationProj,
                  Some(TaskStatus.Validated)
                ),
                dbUser
              )
              fetched <- TaskDao.listProjectTasksByStatus(
                dbAnnotationProj.id,
                List("LABELED", "VALIDATED")
              )
            } yield { (collectionOne, collectionTwo, fetched) }

            val (colOne, colTwo, listed) = connIO.transact(xa).unsafeRunSync
            colOne.features.length + colTwo.features.length == listed.length
          }
      }
    }
  }

  test("create a geometric extent even when no tasks returned in query") {
    check {
      forAll {
        (
            userCreate: User.Create,
            orgCreate: Organization.Create,
            platform: Platform,
            projectCreate: Project.Create,
            annotationProjectCreate: AnnotationProject.Create
        ) =>
          {
            val connIO = for {
              (dbUser, _, _, dbProject) <- insertUserOrgPlatProject(
                userCreate,
                orgCreate,
                platform,
                projectCreate
              )
              dbAnnotationProj <- AnnotationProjectDao
                .insert(
                  annotationProjectCreate.copy(
                    projectId = Some(dbProject.id)
                  ),
                  dbUser
                )
              unionedExtent <- TaskDao.createUnionedGeomExtent(
                dbAnnotationProj.id,
                Nil
              )
            } yield unionedExtent

            val result = connIO.transact(xa).unsafeRunSync
            result should be(None: Option[UnionedGeomExtent])
            true
          }
      }
    }
  }

  test("expire locks on stale tasks") {
    check {
      forAll {
        (
            userCreate: User.Create,
            orgCreate: Organization.Create,
            platform: Platform,
            projectCreate: Project.Create,
            annotationProjectCreate: AnnotationProject.Create,
            taskFeatureCollectionCreate: Task.TaskFeatureCollectionCreate
        ) =>
          {
            val expiryIO = for {
              (dbUser, _, _, dbProject) <- insertUserOrgPlatProject(
                userCreate,
                orgCreate,
                platform,
                projectCreate
              )
              dbAnnotationProj <- AnnotationProjectDao
                .insert(
                  annotationProjectCreate.copy(
                    projectId = Some(dbProject.id)
                  ),
                  dbUser
                )
              insertedTasks <- TaskDao.insertTasks(
                fixupTaskFeaturesCollection(
                  taskFeatureCollectionCreate,
                  dbAnnotationProj,
                  None
                ),
                dbUser
              )
              _ <- insertedTasks.features traverse { task =>
                TaskDao.lockTask(task.id)(dbUser)
              }
              numberExpiredBogus <- TaskDao.expireStuckTasks(9000 seconds)
              numberExpired <- TaskDao.expireStuckTasks(0 seconds)
              listedTasks <- TaskDao.query
                .filter(fr"annotation_project_id = ${dbAnnotationProj.id}")
                .list
            } yield
              (insertedTasks, numberExpiredBogus, numberExpired, listedTasks)

            val (
              insertedTasks,
              numberExpiredBogus,
              numberExpired,
              listedTasks
            ) =
              expiryIO.transact(xa).unsafeRunSync

            val insertedById =
              insertedTasks.features.groupBy(_.id).mapValues(_.head)
            val listedById = listedTasks.groupBy(_.id).mapValues(_.head)

            val statusPairs = insertedById.keys.toList map { taskId =>
              val insertedTaskStatus =
                insertedById.get(taskId).get.properties.status
              val listedTaskStatus = listedById.get(taskId).get.status
              (insertedTaskStatus, listedTaskStatus)
            }

            assert(
              numberExpiredBogus == 0,
              "Expiration leaves fresh tasks alone"
            )

            assert(
              numberExpired == taskFeatureCollectionCreate.features.length,
              "All inserted tasks expired"
            )

            assert(
              (listedTasks flatMap { _.lockedBy }) == Nil,
              "All tasks reverted to not being locked by anyone"
            )

            assert(
              (listedTasks flatMap { _.lockedOn }) == Nil,
              "All tasks reverted to not being locked at any time"
            )

            assert(
              statusPairs.foldLeft(true)(
                (base: Boolean, tup: (TaskStatus, TaskStatus)) => {
                  val (insertedStatus, listedStatus) = tup
                  base &&
                  (insertedStatus === listedStatus
                  || insertedStatus === TaskStatus.LabelingInProgress
                  || insertedStatus === TaskStatus.ValidationInProgress)
                }
              ),
              "Only tasks in progress had their statuses changed"
            )

            true
          }
      }
    }
  }

  test("task unlocking respects most recent status") {
    check {
      forAll {
        (
            userCreate: User.Create,
            orgCreate: Organization.Create,
            platform: Platform,
            annotationProjectCreate: AnnotationProject.Create,
            taskFeatureCollectionCreate: Task.TaskFeatureCollectionCreate,
            firstStatus: TaskStatus,
            nextStatus: TaskStatus,
            finalStatus: TaskStatus
        ) =>
          {
            val maybeNote: TaskStatus => Option[NonEmptyString] = {
              case TaskStatus.Flagged => Some(refineMV("something wrong"))
              case _                  => None
            }

            val baseCreate = taskFeatureCollectionCreate.features.head
            val noteLens: Lens[Task.TaskFeatureCreate, Option[NonEmptyString]] =
              GenLens[Task.TaskFeatureCreate](_.properties.note)

            val expiryIO = for {
              (dbUser, _, _) <- insertUserOrgPlatform(
                userCreate,
                orgCreate,
                platform
              )
              dbAnnotationProject <- AnnotationProjectDao.insert(
                annotationProjectCreate,
                dbUser
              )
              fixedUp = fixupTaskFeaturesCollection(
                taskFeatureCollectionCreate,
                dbAnnotationProject,
                None
              )
              insertedTask <- TaskDao.insertTasks(
                fixedUp.copy(features = List(fixedUp.features.head)),
                dbUser
              ) map { _.features.head }
              fixedUp1 = noteLens.modify(_ => maybeNote(firstStatus))(
                fixupTaskFeatureCreate(
                  baseCreate,
                  dbAnnotationProject,
                  Some(firstStatus)
                )
              )
              fixedUp2 = noteLens.modify(_ => maybeNote(nextStatus))(
                fixupTaskFeatureCreate(
                  baseCreate,
                  dbAnnotationProject,
                  Some(nextStatus)
                )
              )
              fixedUp3 = noteLens.modify(_ => maybeNote(finalStatus))(
                fixupTaskFeatureCreate(
                  baseCreate,
                  dbAnnotationProject,
                  Some(finalStatus)
                )
              )
              _ <- TaskDao.updateTask(
                insertedTask.id,
                fixedUp1,
                dbUser
              )
              _ <- TaskDao.updateTask(
                insertedTask.id,
                fixedUp2,
                dbUser
              )
              _ <- TaskDao.updateTask(
                insertedTask.id,
                fixedUp3,
                dbUser
              )
              _ <- TaskDao.expireStuckTasks(0 seconds)
              retrieved <- TaskDao.unsafeGetTaskById(insertedTask.id)
            } yield retrieved.status

            val postExpirationStatus = expiryIO.transact(xa).unsafeRunSync

            assert(
              postExpirationStatus === finalStatus ||
                (postExpirationStatus === nextStatus && Set[TaskStatus](
                  TaskStatus.LabelingInProgress,
                  TaskStatus.ValidationInProgress
                ).contains(finalStatus)),
              "If validation or labeling was in progress, status reverted to second-to-last"
            )

            true
          }
      }
    }
  }

  test("get a random task") {
    check {
      forAll {
        (
            userCreate1: User.Create,
            userCreate2: User.Create,
            annotationProjectCreate1: AnnotationProject.Create,
            annotationProjectCreate2: AnnotationProject.Create,
            taskFeatureCollectionCreate1: Task.TaskFeatureCollectionCreate,
            taskFeatureCollectionCreate2: Task.TaskFeatureCollectionCreate
        ) =>
          {
            val randomTaskIO = for {
              user1 <- UserDao.create(userCreate1)
              user2 <- UserDao.create(userCreate2)
              dbAnnotationProject1 <- AnnotationProjectDao.insert(
                annotationProjectCreate1,
                user1
              )
              dbAnnotationProject2 <- AnnotationProjectDao.insert(
                annotationProjectCreate2,
                user2
              )
              insertedTasks1 <- TaskDao.insertTasks(
                fixupTaskFeaturesCollection(
                  taskFeatureCollectionCreate1,
                  dbAnnotationProject1,
                  Some(TaskStatus.Unlabeled)
                ),
                user1
              )
              insertedTasks2 <- TaskDao.insertTasks(
                fixupTaskFeaturesCollection(
                  taskFeatureCollectionCreate2,
                  dbAnnotationProject2,
                  None
                ),
                user2
              )
              randomTask1 <- TaskDao.randomTask(
                TaskQueryParameters(
                  status = Some(TaskStatus.Unlabeled)
                ),
                NonEmptyList.one(dbAnnotationProject1.id)
              )
              randomTask2 <- TaskDao.randomTask(
                TaskQueryParameters(),
                NonEmptyList.one(dbAnnotationProject2.id)
              )
              randomTask3 <- TaskDao.randomTask(
                TaskQueryParameters(
                  status = Some(TaskStatus.Validated)
                ),
                NonEmptyList.one(dbAnnotationProject1.id)
              )
            } yield {
              (
                insertedTasks1,
                insertedTasks2,
                randomTask1,
                randomTask2,
                randomTask3
              )
            }

            val (
              project1Tasks,
              project2Tasks,
              randomTask1,
              randomTask2,
              randomTask3
            ) =
              randomTaskIO.transact(xa).unsafeRunSync

            assert(
              project1Tasks.features.contains(randomTask1.get),
              "Random task 1 comes from the first project's tasks"
            )
            assert(
              project2Tasks.features.contains(randomTask2.get),
              "Random task 2 comes from the first project's tasks"
            )
            assert(randomTask3.isEmpty, "Task status filters are respected")

            true
          }
      }
    }
  }
  test("listing some children of a task") {
    check {
      forAll {
        (
            userCreate: User.Create,
            orgCreate: Organization.Create,
            platform: Platform,
            projectCreate: Project.Create,
            taskFeaturesCreate: Task.TaskFeatureCollectionCreate,
            annotationProjectCreate: AnnotationProject.Create
        ) =>
          {
            val connIO =
              for {
                (dbUser, _, _, dbProject) <- insertUserOrgPlatProject(
                  userCreate,
                  orgCreate,
                  platform,
                  projectCreate
                )
                dbAnnotationProj <- AnnotationProjectDao
                  .insert(
                    annotationProjectCreate.copy(
                      projectId = Some(dbProject.id)
                    ),
                    dbUser
                  )
                tasks <- TaskDao.insertTasks(
                  fixupTaskFeaturesCollection(
                    taskFeaturesCreate,
                    dbAnnotationProj
                  ),
                  dbUser
                )
                _ <- tasks.features.tail traverse { f =>
                  TaskDao.updateTask(
                    f.id,
                    Task.TaskFeatureCreate(
                      f.properties.toCreate
                        .copy(parentTaskId = Some(tasks.features.head.id)),
                      f.geometry,
                      "Feature"
                    ),
                    dbUser
                  )
                }
                children <- TaskDao.children(
                  tasks.features.head.properties.id,
                  PageRequest(0, 10, Map.empty)
                )
                parents <- TaskDao.listTasks(
                  TaskQueryParameters(),
                  dbAnnotationProj.id,
                  PageRequest(0, 10, Map.empty)
                )
              } yield { (parents, children) }

            val (parents, children) = connIO.transact(xa).unsafeRunSync
            assert(
              parents.count == 1,
              "Count of parents should be correct"
            )
            assert(
              children.count == taskFeaturesCreate.features.length - 1,
              "Count of children should be correct"
            )

            true
          }
      }
    }
  }

  test("updating reviews of tasks should update review status of their parent") {
    check {
      forAll {
        (
            userCreate: User.Create,
            orgCreate: Organization.Create,
            platform: Platform,
            projectCreate: Project.Create,
            taskFeaturesCreate: Task.TaskFeatureCollectionCreate,
            annotationProjectCreate: AnnotationProject.Create
        ) =>
          {
            val connIO =
              for {
                (dbUser, _, _, dbProject) <- insertUserOrgPlatProject(
                  userCreate,
                  orgCreate,
                  platform,
                  projectCreate
                )
                dbAnnotationProj <- AnnotationProjectDao
                  .insert(
                    annotationProjectCreate.copy(
                      projectId = Some(dbProject.id)
                    ),
                    dbUser
                  )
                tasks <- TaskDao.insertTasks(
                  fixupTaskFeaturesCollection(
                    taskFeaturesCreate,
                    dbAnnotationProj
                  ),
                  dbUser
                )
                parent = tasks.features.take(1)
                childOneFC <- parent traverse { p =>
                  TaskDao.insertTasks(
                    createChildTaskCreateFC(
                      p,
                      TaskStatus.Labeled,
                      Some(TaskType.Review)
                    ),
                    dbUser
                  )
                }
                childTwoFC <- parent traverse { p =>
                  TaskDao.insertTasks(
                    createChildTaskCreateFC(
                      p,
                      TaskStatus.Labeled,
                      Some(TaskType.Review)
                    ),
                    dbUser
                  )
                }
                childThreeFC <- parent traverse { p =>
                  TaskDao.insertTasks(
                    createChildTaskCreateFC(
                      p,
                      TaskStatus.Labeled,
                      Some(TaskType.Review)
                    ),
                    dbUser
                  )
                }
                parentAfterChildrenInsert <- parent traverse { p =>
                  TaskDao.unsafeGetTaskById(p.id)
                }
                _ <- childOneFC traverse { childFC =>
                  childFC.features traverse { feature =>
                    TaskDao.updateTask(
                      feature.id,
                      addReviewToTaskCreate(feature, LabelVoteType.Pass),
                      dbUser
                    )
                  }
                }
                parentAfterChildOneUpdate <- parent traverse { p =>
                  TaskDao.unsafeGetTaskById(p.id)
                }
                _ <- childTwoFC traverse { childFC =>
                  childFC.features traverse { feature =>
                    TaskDao.updateTask(
                      feature.id,
                      addReviewToTaskCreate(feature, LabelVoteType.Fail),
                      dbUser
                    )
                  }
                }
                parentAfterChildTwoUpdate <- parent traverse { p =>
                  TaskDao.unsafeGetTaskById(p.id)
                }
                _ <- childThreeFC traverse { childFC =>
                  childFC.features traverse { feature =>
                    TaskDao.updateTask(
                      feature.id,
                      addReviewToTaskCreate(feature, LabelVoteType.Pass),
                      dbUser
                    )
                  }
                }
                parentAfterChildThreeUpdate <- parent traverse { p =>
                  TaskDao.unsafeGetTaskById(p.id)
                }
                _ <- childTwoFC traverse { childFC =>
                  childFC.features traverse { feature =>
                    TaskDao.updateTask(
                      feature.id,
                      addReviewToTaskCreate(feature, LabelVoteType.Pass),
                      dbUser
                    )
                  }
                }
                parentAfterChildTwoUpdatedWithPass <- parent traverse { p =>
                  TaskDao.unsafeGetTaskById(p.id)
                }
              } yield {
                (
                  parentAfterChildrenInsert,
                  parentAfterChildOneUpdate,
                  parentAfterChildTwoUpdate,
                  parentAfterChildThreeUpdate,
                  parentAfterChildTwoUpdatedWithPass
                )
              }

            val (
              pAfterCInsert,
              pAfterCOneUpdate,
              pAfterCTwoUpdate,
              pAfterCThreeUpdate,
              pAfterCTwoUpdatePass
            ) = connIO.transact(xa).unsafeRunSync

            assert(
              pAfterCInsert
                .map(
                  t => t.reviewStatus == Some(TaskReviewStatus.ReviewPending)
                )
                .toSet === Set(true),
              "Parent task review status is pending after inserting children tasks"
            )
            assert(
              pAfterCOneUpdate
                .map(
                  t => t.reviewStatus == Some(TaskReviewStatus.ReviewPending)
                )
                .toSet === Set(true),
              "Parent task review status is pending after 1 out of 3 children tasks has reviews"
            )
            assert(
              pAfterCTwoUpdate
                .map(
                  t => t.reviewStatus == Some(TaskReviewStatus.ReviewPending)
                )
                .toSet === Set(true),
              "Parent task review status is pending after 2 out of 3 children tasks has reviews"
            )
            assert(
              pAfterCThreeUpdate
                .map(
                  t =>
                    t.reviewStatus == Some(
                      TaskReviewStatus.ReviewNeedsAttention
                    )
                )
                .toSet === Set(true),
              "Parent task review status is needs attention after 3 out of 3 children tasks has reviews"
            )
            assert(
              pAfterCTwoUpdatePass
                .map(
                  t => t.reviewStatus == Some(TaskReviewStatus.ReviewValidated)
                )
                .toSet === Set(true),
              "Parent task review status is validated after all 3 children tasks have Pass votes"
            )
            true
          }
      }
    }
  }
}
