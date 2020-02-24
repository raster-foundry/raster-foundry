package com.rasterfoundry.database

import com.rasterfoundry.common.Generators.Implicits._
import com.rasterfoundry.datamodel._

import doobie.implicits._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatestplus.scalacheck.Checkers

import java.util.UUID

class AnnotationLabelDaoSpec
    extends FunSuite
    with Matchers
    with Checkers
    with DBTestConfig
    with PropTestHelpers {

  def addClasses(
      label: AnnotationLabelWithClasses.Create,
      classes: List[UUID]
  ): AnnotationLabelWithClasses.Create =
    label.copy(annotationLabelClasses = classes.take(1))

  test("insert annotations") {
    check {
      forAll(
        (
            userCreate: User.Create,
            annotationProjectCreate: AnnotationProject.Create,
            annotationCreates: List[AnnotationLabelWithClasses.Create],
            taskFeatureCollectionCreate: Task.TaskFeatureCollectionCreate
        ) => {

          val toInsert = annotationProjectCreate.copy(
            labelClassGroups = annotationProjectCreate.labelClassGroups.take(1)
          )

          val insertIO = for {
            user <- UserDao.create(userCreate)
            annotationProject <- AnnotationProjectDao
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
            created <- AnnotationLabelDao.insertAnnotations(
              annotationProject.id,
              task.id,
              annotationCreates map { create =>
                addClasses(create, classIds)
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
            taskFeatureCollectionCreate: Task.TaskFeatureCollectionCreate
        ) => {
          val toInsert = annotationProjectCreate.copy(
            labelClassGroups = annotationProjectCreate.labelClassGroups.take(1)
          )

          val listIO = for {
            user <- UserDao.create(userCreate)
            annotationProject <- AnnotationProjectDao
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
            withClasses = annotationCreates map { create =>
              addClasses(create, classIds)
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
            taskFeatureCollectionCreate: Task.TaskFeatureCollectionCreate
        ) => {
          val toInsert = annotationProjectCreate.copy(
            labelClassGroups = annotationProjectCreate.labelClassGroups.take(1)
          )

          val listIO = for {
            user <- UserDao.create(userCreate)
            annotationProject <- AnnotationProjectDao
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
            withClasses = annotationCreates map { create =>
              addClasses(create, classIds)
            }
            _ <- AnnotationLabelDao.insertAnnotations(
              annotationProject.id,
              task.id,
              withClasses,
              user
            )
            listedByTask <- AnnotationLabelDao
              .listWithClassesByProjectIdAndTaskId(
                annotationProject.id,
                task.id
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
            taskFeatureCollectionCreate: Task.TaskFeatureCollectionCreate
        ) => {
          val toInsert = annotationProjectCreate.copy(
            labelClassGroups = annotationProjectCreate.labelClassGroups.take(1)
          )

          val summaryIO = for {
            user <- UserDao.create(userCreate)
            annotationProject <- AnnotationProjectDao
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
            withClasses = annotationCreates map { create =>
              addClasses(create, classIds)
            }
            _ <- AnnotationLabelDao.insertAnnotations(
              annotationProject.id,
              task.id,
              withClasses,
              user
            )
            summaryReal <- AnnotationLabelDao.countByProjectAndGroup(
              annotationProject.id,
              annotationProject.labelClassGroups.head.id
            )
            summaryBogus <- AnnotationLabelDao.countByProjectAndGroup(
              annotationProject.id,
              UUID.randomUUID
            )
          } yield { (classIds, summaryReal, summaryBogus) }

          val (classIds, summaryReal, summaryBogus) =
            summaryIO.transact(xa).unsafeRunSync

          classIds map {
            classId =>
              val labelSummaryO = summaryReal.find(_.labelClassId == classId)
              val expectation = if (annotationCreates.isEmpty) {
                None
              } else {
                Some(annotationCreates.size)
              }
              assert(
                (labelSummaryO map {
                  (summ: AnnotationProject.LabelClassSummary) =>
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
            taskFeatureCollectionCreate: Task.TaskFeatureCollectionCreate
        ) => {
          val labelClassGroupCreate =
            annotationProjectCreate.labelClassGroups.take(1)
          val toInsert = annotationProjectCreate.copy(
            labelClassGroups = labelClassGroupCreate
          )

          val listIO = for {
            user <- UserDao.create(userCreate)
            annotationProject <- AnnotationProjectDao
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
            withClasses = annotationCreates map { create =>
              addClasses(create, classIds)
            }
            _ <- AnnotationLabelDao.insertAnnotations(
              annotationProject.id,
              task.id,
              withClasses,
              user
            )
            _ <- AnnotationLabelDao
              .deleteByProjectIdAndTaskId(annotationProject.id, task.id)
            listedByTask <- AnnotationLabelDao
              .listWithClassesByProjectIdAndTaskId(
                annotationProject.id,
                task.id
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
            taskFeatureCollectionCreate: Task.TaskFeatureCollectionCreate
        ) => {
          val toInsert = annotationProjectCreate.copy(
            labelClassGroups = annotationProjectCreate.labelClassGroups.take(1)
          )

          val listIO = for {
            user <- UserDao.create(userCreate)
            annotationProject <- AnnotationProjectDao
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
            withClasses = annotationCreates map { create =>
              addClasses(create, classIds)
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

          val stacAnnotationsO = listIO.transact(xa).unsafeRunSync

          assert(stacAnnotationsO.nonEmpty, "Annotations json is created")
          val stacAnnotationsJson = stacAnnotationsO.map(_.asObject).flatten.get
          val requiredLabelFields = Set("geometry", "type", "properties")
          assert(
            requiredLabelFields subsetOf stacAnnotationsJson.keys.toSet,
            "stac label json contains correct top level fields"
          )
          val labelProperties = stacAnnotationsJson.toMap
            .get("properties")
            .map(_.asObject)
            .flatten
            .get
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
          val groupName = labelClassGroupCreate.head.name
          assert(
            labelProperties.keys.toSet.contains(groupName),
            "stac label class group / label value exists"
          )
          assert(
            labelClassGroupCreate.head.classes
              .map(_.name)
              .toSet
              .contains(labelProperties.toMap.get(groupName).get.asString),
              "stac label value for group name exists"
          )
          true
        }
      )
    }
  }
}
