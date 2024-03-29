package com.rasterfoundry.database

import com.rasterfoundry.common.Generators.Implicits._
import com.rasterfoundry.datamodel._

import doobie.implicits._
import org.scalacheck.Prop.forAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.Checkers

import java.util.UUID

class AnnotationLabelDaoSpec
    extends AnyFunSuite
    with Matchers
    with Checkers
    with DBTestConfig
    with PropTestHelpers {

  test("insert annotations") {
    check {
      forAll(
        (
            userCreate: User.Create,
            annotationProjectCreate: AnnotationProject.Create,
            annotationCreates: List[AnnotationLabelWithClasses.Create],
            taskFeatureCollectionCreate: Task.TaskFeatureCollectionCreate,
            taskSessionCreate: TaskSession.Create
        ) => {

          val toInsert = annotationProjectCreate.copy(
            labelClassGroups = annotationProjectCreate.labelClassGroups.take(1)
          )

          val insertIO = for {
            user <- UserDao.create(userCreate)
            annotationProject <-
              AnnotationProjectDao
                .insert(toInsert, user)
            classIds = annotationProject.labelClassGroups flatMap {
              _.labelClasses
            } map { _.id }
            fixedUpTasks = fixupTaskFeaturesCollection(
              taskFeatureCollectionCreate,
              annotationProject,
              None
            )
            task <- TaskDao.insertTasks(
              fixedUpTasks.copy(features = fixedUpTasks.features.take(1)),
              user
            ) map { _.features.head }
            dbTaskSession <-
              TaskSessionDao
                .insertTaskSession(
                  taskSessionCreate,
                  user,
                  task.properties.status,
                  task.id
                )
            created <- AnnotationLabelDao.insertAnnotations(
              annotationProject.id,
              task.id,
              annotationCreates map { create =>
                addClasses(create, classIds)
                  .copy(sessionId = Some(dbTaskSession.id))
              },
              user
            )
          } yield (created)

          val created = insertIO.transact(xa).unsafeRunSync
          assert(
            created.size == annotationCreates.size,
            "All the annotations were inserted"
          )

          true
        }
      )
    }
  }

  test("list labels for a project") {
    check {
      forAll(
        (
            userCreate: User.Create,
            annotationProjectCreate: AnnotationProject.Create,
            annotationCreates: List[AnnotationLabelWithClasses.Create],
            taskFeatureCollectionCreate: Task.TaskFeatureCollectionCreate,
            taskSessionCreate: TaskSession.Create
        ) => {
          val toInsert = annotationProjectCreate.copy(
            labelClassGroups = annotationProjectCreate.labelClassGroups.take(1)
          )

          val listIO = for {
            user <- UserDao.create(userCreate)
            annotationProject <-
              AnnotationProjectDao
                .insert(toInsert, user)
            fixedUpTasks = fixupTaskFeaturesCollection(
              taskFeatureCollectionCreate,
              annotationProject,
              None
            )
            task <- TaskDao.insertTasks(
              fixedUpTasks.copy(features = fixedUpTasks.features.take(1)),
              user
            ) map { _.features.head }
            classIds = annotationProject.labelClassGroups flatMap {
              _.labelClasses
            } map { _.id }
            dbTaskSession <-
              TaskSessionDao
                .insertTaskSession(
                  taskSessionCreate,
                  user,
                  task.properties.status,
                  task.id
                )
            withClasses = annotationCreates map { create =>
              addClasses(create, classIds).copy(sessionId =
                Some(dbTaskSession.id)
              )
            }
            _ <- AnnotationLabelDao.insertAnnotations(
              annotationProject.id,
              task.id,
              withClasses,
              user
            )
            listed <- AnnotationLabelDao.listProjectLabels(annotationProject.id)
          } yield listed

          val listed = listIO.transact(xa).unsafeRunSync

          assert(
            listed.size == annotationCreates.size,
            "All annotations were listed"
          )

          true
        }
      )
    }
  }

  test("list labels for a project task") {
    check {
      forAll(
        (
            userCreate: User.Create,
            annotationProjectCreate: AnnotationProject.Create,
            annotationCreates: List[AnnotationLabelWithClasses.Create],
            taskFeatureCollectionCreate: Task.TaskFeatureCollectionCreate,
            taskSessionCreate: TaskSession.Create
        ) => {
          val toInsert = annotationProjectCreate.copy(
            labelClassGroups = annotationProjectCreate.labelClassGroups.take(1)
          )

          val listIO = for {
            user <- UserDao.create(userCreate)
            annotationProject <-
              AnnotationProjectDao
                .insert(toInsert, user)
            fixedUpTasks = fixupTaskFeaturesCollection(
              taskFeatureCollectionCreate,
              annotationProject,
              None
            )
            task <- TaskDao.insertTasks(
              fixedUpTasks.copy(features = fixedUpTasks.features.take(1)),
              user
            ) map { _.features.head }
            classIds = annotationProject.labelClassGroups flatMap {
              _.labelClasses
            } map { _.id }
            dbTaskSession <-
              TaskSessionDao
                .insertTaskSession(
                  taskSessionCreate,
                  user,
                  task.properties.status,
                  task.id
                )
            withClasses = annotationCreates map { create =>
              addClasses(create, classIds).copy(sessionId =
                Some(dbTaskSession.id)
              )
            }
            _ <- AnnotationLabelDao.insertAnnotations(
              annotationProject.id,
              task.id,
              withClasses,
              user
            )
            listedByTask <-
              AnnotationLabelDao
                .listWithClassesByProjectIdAndTaskId(
                  annotationProject.id,
                  task.id,
                  TaskLabelQueryParameters(hitlVersionId = None)
                )
          } yield listedByTask

          val listedByTask = listIO.transact(xa).unsafeRunSync

          assert(
            listedByTask.size == annotationCreates.size,
            "All annotations from a task were listed"
          )

          true
        }
      )
    }
  }

  test("get project summary for an annotation group") {
    check {
      forAll(
        (
            userCreate: User.Create,
            annotationProjectCreate: AnnotationProject.Create,
            annotationCreates: List[AnnotationLabelWithClasses.Create],
            taskFeatureCollectionCreate: Task.TaskFeatureCollectionCreate,
            taskSessionCreate: TaskSession.Create
        ) => {
          val summaryIO = for {
            user <- UserDao.create(userCreate)
            annotationProject <-
              AnnotationProjectDao
                .insert(annotationProjectCreate, user)
            fixedUpTasks = fixupTaskFeaturesCollection(
              taskFeatureCollectionCreate,
              annotationProject,
              None
            )
            task <- TaskDao.insertTasks(
              fixedUpTasks.copy(features = fixedUpTasks.features.take(1)),
              user
            ) map { _.features.head }
            classIds =
              annotationProject.labelClassGroups.head.labelClasses map {
                _.id
              }
            dbTaskSession <-
              TaskSessionDao
                .insertTaskSession(
                  taskSessionCreate,
                  user,
                  task.properties.status,
                  task.id
                )
            withClasses = annotationCreates map { create =>
              addClasses(create, classIds).copy(sessionId =
                Some(dbTaskSession.id)
              )
            }
            _ <- AnnotationLabelDao.insertAnnotations(
              annotationProject.id,
              task.id,
              withClasses,
              user
            )
            summaryReal <- AnnotationLabelDao.countByProjectsAndGroup(
              List(annotationProject.id),
              annotationProject.labelClassGroups.head.id
            )
            summaryBogus <- AnnotationLabelDao.countByProjectsAndGroup(
              List(annotationProject.id),
              UUID.randomUUID
            )
          } yield { (classIds, summaryReal, summaryBogus) }

          val (classIds, summaryReal, summaryBogus) =
            summaryIO.transact(xa).unsafeRunSync

          classIds map { classId =>
            val labelSummaryO = summaryReal.find(_.labelClassId == classId)
            val expectation = if (annotationCreates.isEmpty) {
              None
            } else {
              Some(annotationCreates.size)
            }
            assert(
              (labelSummaryO map { (summ: LabelClassSummary) =>
                summ.count
              }) == expectation,
              "All the annotations with the real class were counted"
            )
          }

          assert(
            summaryBogus == Nil,
            "Lookup by a bogus group id returns nothing"
          )

          true
        }
      )
    }
  }

  test("delete labels from a project task") {
    check {
      forAll(
        (
            userCreate: User.Create,
            annotationProjectCreate: AnnotationProject.Create,
            annotationCreates: List[AnnotationLabelWithClasses.Create],
            taskFeatureCollectionCreate: Task.TaskFeatureCollectionCreate,
            taskSessionCreate: TaskSession.Create
        ) => {
          val listIO = for {
            user <- UserDao.create(userCreate)
            annotationProject <-
              AnnotationProjectDao
                .insert(annotationProjectCreate, user)
            fixedUpTasks = fixupTaskFeaturesCollection(
              taskFeatureCollectionCreate,
              annotationProject,
              None
            )
            task <- TaskDao.insertTasks(
              fixedUpTasks.copy(features = fixedUpTasks.features.take(1)),
              user
            ) map { _.features.head }
            classIds = annotationProject.labelClassGroups flatMap {
              _.labelClasses
            } map { _.id }
            dbTaskSession <-
              TaskSessionDao
                .insertTaskSession(
                  taskSessionCreate,
                  user,
                  task.properties.status,
                  task.id
                )
            withClasses = annotationCreates map { create =>
              addClasses(create, classIds).copy(sessionId =
                Some(dbTaskSession.id)
              )
            }
            _ <- AnnotationLabelDao.insertAnnotations(
              annotationProject.id,
              task.id,
              withClasses,
              user
            )
            _ <-
              AnnotationLabelDao
                .deleteByProjectIdAndTaskId(annotationProject.id, task.id)
            listedByTask <-
              AnnotationLabelDao
                .listWithClassesByProjectIdAndTaskId(
                  annotationProject.id,
                  task.id,
                  TaskLabelQueryParameters(hitlVersionId = None)
                )
          } yield listedByTask

          val listed = listIO.transact(xa).unsafeRunSync

          assert(
            listed.size == 0,
            "All annotations from a task were deleted"
          )

          true
        }
      )
    }
  }

  test("generate stac labels in a project for export") {
    check {
      forAll(
        (
            userCreate: User.Create,
            annotationProjectCreate: AnnotationProject.Create,
            annotationCreates: List[AnnotationLabelWithClasses.Create],
            taskFeatureCollectionCreate: Task.TaskFeatureCollectionCreate,
            taskSessionCreate: TaskSession.Create
        ) => {
          val listIO = for {
            user <- UserDao.create(userCreate)
            annotationProject <-
              AnnotationProjectDao
                .insert(annotationProjectCreate, user)
            fixedUpTasks = fixupTaskFeaturesCollection(
              taskFeatureCollectionCreate,
              annotationProject,
              None
            )
            task <- TaskDao.insertTasks(
              fixedUpTasks.copy(features = fixedUpTasks.features.take(1)),
              user
            ) map { _.features.head }
            classIds =
              annotationProject.labelClassGroups.head.labelClasses map {
                _.id
              }
            dbTaskSession <-
              TaskSessionDao
                .insertTaskSession(
                  taskSessionCreate,
                  user,
                  task.properties.status,
                  task.id
                )
            withClasses = annotationCreates map { create =>
              addClasses(create, classIds).copy(sessionId =
                Some(dbTaskSession.id)
              )
            }
            _ <- AnnotationLabelDao.insertAnnotations(
              annotationProject.id,
              task.id,
              withClasses,
              user
            )
            annotationJson <- AnnotationLabelDao.getAnnotationJsonByTaskStatus(
              annotationProject.id,
              List(task.properties.status.toString)
            )
          } yield annotationJson

          val annotationsO = listIO.transact(xa).unsafeRunSync
          annotationsO
            .flatMap(annotations => {
              val annotationsJson = annotations.asObject.get
              assert(
                annotationsJson.keys.toSet.contains("features"),
                "stac annotation has features property"
              )
              val feats =
                annotationsJson.toMap
                  .get("features")
                  .map(_.asArray)
                  .flatten
                  .get
              assert(
                feats.size == annotationCreates.size,
                "all inserted features are in export"
              )
              feats.headOption.map(_.asObject)
            })
            .flatten
            .map(feature => {
              val requiredLabelFields =
                Set("geometry", "type", "properties")
              assert(
                requiredLabelFields subsetOf feature.keys.toSet,
                "stac label json contains correct top level fields"
              )
              val labelPropertiesO = feature.toMap
                .get("properties")
                .map(_.asObject)
                .flatten
              assert(labelPropertiesO.nonEmpty, "label properties are nonempty")
              val labelProperties = labelPropertiesO.get
              val requiredLabelProperties =
                Set(
                  "id",
                  "createdAt",
                  "createdBy",
                  "annotationProjectId",
                  "annotationTaskId"
                )
              assert(
                requiredLabelProperties subsetOf labelProperties.keys.toSet,
                "stac label json properties contain required normal fields"
              )
              val group = annotationProjectCreate.labelClassGroups.head
              val groupName = group.name
              assert(
                labelProperties.keys.toSet.contains(groupName),
                "stac label class group / label value exists"
              )
              assert(
                group.classes
                  .map(_.name)
                  .toSet
                  .contains(
                    labelProperties.toMap
                      .get(groupName)
                      .map(_.asString)
                      .flatten
                      .get
                  ),
                "stac label value for group name exists"
              )
            })

          true
        }
      )
    }
  }

  test("toggle label by session") {
    check {
      forAll(
        (
            userCreate: User.Create,
            annotationProjectCreate: AnnotationProject.Create,
            annotationCreates: List[AnnotationLabelWithClasses.Create],
            taskFeatureCollectionCreate: Task.TaskFeatureCollectionCreate,
            taskSessionCreate: TaskSession.Create
        ) => {

          val toInsert = annotationProjectCreate.copy(
            labelClassGroups = annotationProjectCreate.labelClassGroups.take(1)
          )

          val insertIO = for {
            user <- UserDao.create(userCreate)
            annotationProject <-
              AnnotationProjectDao
                .insert(toInsert, user)
            classIds = annotationProject.labelClassGroups flatMap {
              _.labelClasses
            } map { _.id }
            fixedUpTasks = fixupTaskFeaturesCollection(
              taskFeatureCollectionCreate,
              annotationProject,
              None
            )
            task <- TaskDao.insertTasks(
              fixedUpTasks.copy(features = fixedUpTasks.features.take(1)),
              user
            ) map { _.features.head }
            dbTaskSession <-
              TaskSessionDao
                .insertTaskSession(
                  taskSessionCreate,
                  user,
                  task.properties.status,
                  task.id
                )
            _ <- AnnotationLabelDao.insertAnnotations(
              annotationProject.id,
              task.id,
              annotationCreates map { create =>
                addClasses(create, classIds)
                  .copy(sessionId = Some(dbTaskSession.id))
              },
              user
            )
            _ <- AnnotationLabelDao.toggleBySessionId(dbTaskSession.id)
            activeLabels <-
              AnnotationLabelDao.listWithClassesByProjectIdAndTaskId(
                annotationProject.id,
                task.id,
                TaskLabelQueryParameters(hitlVersionId = None)
              )
            _ <- AnnotationLabelDao.toggleBySessionId(dbTaskSession.id)
            reactiveLabels <-
              AnnotationLabelDao.listWithClassesByProjectIdAndTaskId(
                annotationProject.id,
                task.id,
                TaskLabelQueryParameters(hitlVersionId = None)
              )
          } yield (activeLabels, reactiveLabels)

          val (inactivated, activated) = insertIO.transact(xa).unsafeRunSync
          assert(
            inactivated.size == 0,
            "Labels are toggled off"
          )

          assert(
            activated.size == annotationCreates.size,
            "Labels are toggled on"
          )

          true
        }
      )
    }
  }

  test("check annotation project has predictions") {
    check {
      forAll {
        (
            userCreate: User.Create,
            annotationProjectCreate: AnnotationProject.Create,
            annotationCreates: List[AnnotationLabelWithClasses.Create],
            taskFeatureCollectionCreate: Task.TaskFeatureCollectionCreate,
            taskSessionCreate: TaskSession.Create
        ) =>
          {

            val toInsert = annotationProjectCreate.copy(
              labelClassGroups =
                annotationProjectCreate.labelClassGroups.take(1)
            )

            val insertIO = for {
              user <- UserDao.create(userCreate)
              annotationProject <-
                AnnotationProjectDao
                  .insert(toInsert, user)
              classIds = annotationProject.labelClassGroups flatMap {
                _.labelClasses
              } map { _.id }
              fixedUpTasks = fixupTaskFeaturesCollection(
                taskFeatureCollectionCreate,
                annotationProject,
                None
              )
              task <- TaskDao.insertTasks(
                fixedUpTasks.copy(features = fixedUpTasks.features.take(1)),
                user
              ) map { _.features.head }
              dbTaskSession <-
                TaskSessionDao
                  .insertTaskSession(
                    taskSessionCreate,
                    user,
                    task.properties.status,
                    task.id
                  )
              _ <- AnnotationLabelDao.insertAnnotations(
                annotationProject.id,
                task.id,
                annotationCreates map { create =>
                  addClasses(create, classIds)
                    .copy(sessionId = Some(dbTaskSession.id))
                },
                user
              )
              hasScores <- AnnotationLabelDao.hasPredictionAnnotationLabels(
                annotationProject.id
              )
            } yield hasScores

            val hasScores = insertIO.transact(xa).unsafeRunSync
            assert(
              hasScores === annotationCreates.exists(create =>
                create.score.isDefined
              ),
              "Project should have scores when input labels have scores"
            )

            true
          }
      }
    }

  }

  test("check filter task labels on HITL version ID") {
    check {
      forAll(
        (
            userCreate: User.Create,
            userCreateTwo: User.Create,
            annotationProjectCreate: AnnotationProject.Create,
            annotationCreates: List[AnnotationLabelWithClasses.Create],
            taskFeatureCollectionCreate: Task.TaskFeatureCollectionCreate,
            taskSessionCreate: TaskSession.Create,
            campaignCreate: Campaign.Create,
            hitlJobCreate: HITLJob.Create
        ) => {

          val toInsert = annotationProjectCreate.copy(
            labelClassGroups = annotationProjectCreate.labelClassGroups.take(1)
          )

          val insertIO = for {
            user <- UserDao.create(userCreate)
            userTwo <- UserDao.create(userCreateTwo)
            campaign <-
              CampaignDao
                .insertCampaign(
                  campaignCreate.copy(parentCampaignId = None),
                  user
                )
            annotationProject <-
              AnnotationProjectDao
                .insert(toInsert.copy(campaignId = Some(campaign.id)), user)
            classIds = annotationProject.labelClassGroups flatMap {
              _.labelClasses
            } map { _.id }
            fixedUpTasks = fixupTaskFeaturesCollection(
              taskFeatureCollectionCreate,
              annotationProject,
              None
            )
            task <- TaskDao.insertTasks(
              fixedUpTasks.copy(features = fixedUpTasks.features.take(1)),
              user
            ) map { _.features.head }
            dbTaskSession <-
              TaskSessionDao
                .insertTaskSession(
                  taskSessionCreate,
                  user,
                  task.properties.status,
                  task.id
                )
            created <- AnnotationLabelDao.insertAnnotations(
              annotationProject.id,
              task.id,
              annotationCreates map { create =>
                addClasses(create, classIds)
                  .copy(sessionId = Some(dbTaskSession.id))
              },
              user
            )
            createdTwo <- AnnotationLabelDao.insertAnnotations(
              annotationProject.id,
              task.id,
              annotationCreates map { create =>
                addClasses(create, classIds)
                  .copy(sessionId = Some(dbTaskSession.id))
              },
              userTwo
            )
            hitlJob <- HITLJobDao.create(
              hitlJobCreate
                .copy(
                  campaignId = campaign.id,
                  projectId = annotationProject.id
                ),
              user
            )
            machineCreated <- AnnotationLabelDao.insertAnnotations(
              annotationProject.id,
              task.id,
              annotationCreates map { create =>
                addClasses(create, classIds)
                  .copy(
                    sessionId = Some(dbTaskSession.id),
                    hitlVersionId = Some(hitlJob.id)
                  )
              },
              user
            )
            listedHuman <-
              AnnotationLabelDao.listWithClassesByProjectIdAndTaskId(
                annotationProject.id,
                task.id,
                TaskLabelQueryParameters(hitlVersionId = None)
              )
            listedMachine <-
              AnnotationLabelDao.listWithClassesByProjectIdAndTaskId(
                annotationProject.id,
                task.id,
                TaskLabelQueryParameters(hitlVersionId = Some(hitlJob.id))
              )
            isHITLInProgress <- HITLJobDao.hasInProgressJob(
              campaign.id,
              annotationProject.id,
              user
            )
            _ <- HITLJobDao.update(
              hitlJob.copy(status = HITLJobStatus.Ran),
              hitlJob.id
            )
            isHITLInProgressAfter <- HITLJobDao.hasInProgressJob(
              campaign.id,
              annotationProject.id,
              user
            )
          } yield {
            (
              created,
              createdTwo,
              machineCreated,
              listedHuman,
              listedMachine,
              isHITLInProgress,
              isHITLInProgressAfter
            )
          }

          val (
            created,
            createdAdditional,
            machine,
            humanList,
            machineList,
            isJobInProgress,
            isJobInProgressAfter
          ) =
            insertIO.transact(xa).unsafeRunSync

          assert(
            created.size == annotationCreates.size,
            "All the annotations were inserted"
          )
          assert(
            (created.map(_.id) ++ createdAdditional.map(
              _.id
            )).toSet == humanList.map(_.id).toSet,
            "List method return human labels by default"
          )
          assert(
            machine.map(_.id).toSet == machineList.map(_.id).toSet,
            "List method can return machine labels too"
          )
          assert(
            isJobInProgress == true,
            "HITL job should be in progress before updating"
          )
          assert(
            isJobInProgressAfter == false,
            "HITL job should not be in progress after updating"
          )
          true
        }
      )
    }
  }
}
