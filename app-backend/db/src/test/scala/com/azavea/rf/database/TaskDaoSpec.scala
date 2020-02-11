package com.rasterfoundry.database

import com.rasterfoundry.datamodel._
import com.rasterfoundry.datamodel.GeoJsonCodec.PaginatedGeoJsonResponse
import com.rasterfoundry.common.Generators.Implicits._

import cats.data.NonEmptyList
import cats.implicits._
import doobie.ConnectionIO
import doobie.implicits._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatestplus.scalacheck.Checkers
import com.typesafe.scalalogging.LazyLogging

class TaskDaoSpec
    extends FunSuite
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
                    dbProject,
                    dbAnnotationProj
                  ),
                  dbUser
                )
                fetched <- TaskDao.listTasks(
                  TaskQueryParameters(),
                  dbProject.id,
                  dbProject.defaultLayerId,
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
            val connIO: ConnectionIO[(Task.TaskFeatureCollection, List[Task])] =
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
                    dbProject,
                    dbAnnotationProj
                  ),
                  dbUser
                )
                fetched <- collection.features traverse { feat =>
                  TaskDao.unsafeGetTaskById(feat.id)
                }
              } yield { (collection, fetched) }

            val (featureCollection, fetched) = connIO.transact(xa).unsafeRunSync
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
                  Int
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
                    dbProject,
                    dbAnnotationProj
                  ),
                  taskGridFeatureCreate,
                  dbUser
                )
              } yield { (createdScene, taskGridFeatureCreate, taskCount) }

            val (createdScene, gridFeatures, taskCount) =
              connIO.transact(xa).unsafeRunSync

            (createdScene, gridFeatures.geometry) match {
              case (_, Some(_)) | (Some(_), None) =>
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
                    dbProject,
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
            taskFeatureCreate1: Task.TaskFeatureCreate,
            taskFeatureCreate2: Task.TaskFeatureCreate,
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
                      taskFeatureCreate1,
                      dbProject,
                      dbAnnotationProj
                    )
                  )
                ),
                dbUser
              )
              update <- TaskDao.updateTask(
                collection.features.head.id,
                fixupTaskFeatureCreate(
                  taskFeatureCreate2,
                  dbProject,
                  dbAnnotationProj
                ),
                dbUser
              )
              // have to delete actions on the task to be able to delete it
              _ <- fr"TRUNCATE TABLE task_actions;".update.run
              delete <- TaskDao.deleteTask(collection.features.head.id)
            } yield (update, delete)
            val (updateResult, deleteResult) = connIO.transact(xa).unsafeRunSync

            if (taskFeatureCreate1.properties.status == taskFeatureCreate2.properties.status) {
              updateResult.get.properties.actions.length should be(0)
            } else {
              updateResult.get.properties.actions.length should be(1)
            }

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
                        dbProject,
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
                  dbProject.id,
                  dbProject.defaultLayerId,
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
                      dbProject,
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

  test("delete all tasks in a project layer") {
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
                    dbProject,
                    dbAnnotationProj
                  ),
                  dbUser
                )
                fetched <- TaskDao.listTasks(
                  TaskQueryParameters(),
                  dbProject.id,
                  dbProject.defaultLayerId,
                  PageRequest(0, 10, Map.empty)
                )
                deletedRowCount <- TaskDao.deleteLayerTasks(
                  dbProject.id,
                  dbProject.defaultLayerId
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
              (dbUser, dbOrg, dbPlatform, dbProject) <- insertUserOrgPlatProject(
                userCreate,
                orgCreate,
                platform,
                projectCreate
              )
              updatedDbProject <- fixupProjectExtrasUpdate(
                labelValidateTeamCreate,
                labelValidateTeamUgrCreate,
                dbOrg,
                dbUser,
                dbPlatform,
                dbProject
              )
              dbAnnotationProj <- AnnotationProjectDao
                .insert(
                  annotationProjectCreate.copy(
                    projectId = Some(updatedDbProject.id)
                  ),
                  dbUser
                )
              collection <- TaskDao.insertTasks(
                Task.TaskFeatureCollectionCreate(
                  features = List(
                    fixupTaskFeatureCreate(
                      taskFeatureCreate,
                      updatedDbProject,
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
                updatedDbProject.id,
                updatedDbProject.defaultLayerId,
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
              (dbUser, dbOrg, dbPlatform, dbProject) <- insertUserOrgPlatProject(
                userCreate,
                orgCreate,
                platform,
                projectCreate
              )
              updatedDbProject <- fixupProjectExtrasUpdate(
                labelValidateTeamCreate,
                labelValidateTeamUgrCreate,
                dbOrg,
                dbUser,
                dbPlatform,
                dbProject
              )
              dbAnnotationProj <- AnnotationProjectDao
                .insert(
                  annotationProjectCreate.copy(
                    projectId = Some(updatedDbProject.id)
                  ),
                  dbUser
                )
              collection <- TaskDao.insertTasks(
                Task.TaskFeatureCollectionCreate(
                  features = List(
                    fixupTaskFeatureCreate(
                      taskFeatureCreate,
                      updatedDbProject,
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
                updatedDbProject.id,
                updatedDbProject.defaultLayerId,
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
              (dbUser, dbOrg, dbPlatform, dbProject) <- insertUserOrgPlatProject(
                userCreate,
                orgCreate,
                platform,
                projectCreate
              )
              updatedDbProject <- fixupProjectExtrasUpdate(
                labelValidateTeamCreate,
                labelValidateTeamUgrCreate,
                dbOrg,
                dbUser,
                dbPlatform,
                dbProject
              )
              dbAnnotationProj <- AnnotationProjectDao
                .insert(
                  annotationProjectCreate.copy(
                    projectId = Some(updatedDbProject.id)
                  ),
                  dbUser
                )
              collection <- TaskDao.insertTasks(
                Task.TaskFeatureCollectionCreate(
                  features = List(
                    fixupTaskFeatureCreate(
                      taskFeatureCreate,
                      updatedDbProject,
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
                updatedDbProject.id,
                updatedDbProject.defaultLayerId,
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
              (dbUser, dbOrg, dbPlatform, dbProject) <- insertUserOrgPlatProject(
                userCreate,
                orgCreate,
                platform,
                projectCreate
              )
              updatedDbProject <- fixupProjectExtrasUpdate(
                labelValidateTeamCreate,
                labelValidateTeamUgrCreate,
                dbOrg,
                dbUser,
                dbPlatform,
                dbProject
              )
              dbAnnotationProj <- AnnotationProjectDao
                .insert(
                  annotationProjectCreate.copy(
                    projectId = Some(updatedDbProject.id)
                  ),
                  dbUser
                )
              _ <- TaskDao.insertTasks(
                Task.TaskFeatureCollectionCreate(
                  features = List(
                    fixupTaskFeatureCreate(
                      taskFeatureCreate,
                      updatedDbProject,
                      dbAnnotationProj
                    ).withStatus(TaskStatus.Unlabeled)
                  )
                ),
                dbUser
              )
              listed <- TaskDao.getTaskUserSummary(
                updatedDbProject.id,
                updatedDbProject.defaultLayerId,
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
                  dbProject,
                  dbAnnotationProj,
                  Some(TaskStatus.Labeled)
                ),
                dbUser
              )
              collectionTwo <- TaskDao.insertTasks(
                fixupTaskFeaturesCollection(
                  taskFeaturesCreateTwo,
                  dbProject,
                  dbAnnotationProj,
                  Some(TaskStatus.Validated)
                ),
                dbUser
              )
              fetched <- TaskDao.listLayerTasksByStatus(
                dbProject.id,
                dbProject.defaultLayerId,
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
            projectCreate: Project.Create
        ) =>
          {
            val connIO = for {
              (_, _, _, dbProject) <- insertUserOrgPlatProject(
                userCreate,
                orgCreate,
                platform,
                projectCreate
              )
              unionedExtent <- TaskDao.createUnionedGeomExtent(
                dbProject.id,
                dbProject.defaultLayerId,
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
}
