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
    label.copy(annotationLabelClasses = classes)

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

          assert(listed.size == annotationCreates.size,
                 "All annotations were listed")

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
}
