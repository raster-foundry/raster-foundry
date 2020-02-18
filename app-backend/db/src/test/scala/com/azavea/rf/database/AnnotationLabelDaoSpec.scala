package com.rasterfoundry.database

import com.rasterfoundry.common.Generators.Implicits._
import com.rasterfoundry.datamodel._

import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatestplus.scalacheck.Checkers

class AnnotationLabelDaoSpec
    extends FunSuite
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
            taskFeatureCollectionCreate: Task.TaskFeatureCollectionCreate
        ) => {
          for {
            user <- UserDao.create(userCreate)
            annotationProject <- AnnotationProjectDao.insert(annotationProjectCreate, user)
            fixedUpTasks = fixupTaskFeaturesCollection(
              taskFeatureCollectionCreate,
              annotationProject,
              None
            )
            task <- TaskDao.insertTasks(
              fixedUpTasks.copy(features = fixedUpTasks.features.take(1)), user
            ) map { _.features.head }
            created <- AnnotationLabelDao.insertAnnotations(
              annotationProject.id,
              task.id,
              annotationCreates,
              user
            )
          } yield (created)
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
            annotationCreates: List[AnnotationLabelWithClasses.Create]
        ) => {
          // TODO
          // - insert the labels after fixing them up to have classes from the annotation project create
          // - count the instance of each class after fixup
          // - get the summary
          // - confirm it matches what we counted in Scala
          true
        }
      )
    }
  }
}
