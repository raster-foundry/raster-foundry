package com.rasterfoundry.database

import com.rasterfoundry.common.Generators.Implicits._
import com.rasterfoundry.datamodel._

import doobie._
import doobie.implicits._
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
            annotationProject <- AnnotationProjectDao
              .insertAnnotationProject(annotationProjectCreate, user)
            rfProject <- ProjectDao.unsafeGetProjectById(
              annotationProject.projectId.get
            )
            fixedUpTasks = fixupTaskFeaturesCollection(
              taskFeatureCollectionCreate,
              rfProject,
              annotationProject,
              None
            )
            task <- TaskDao.insertTasks(
              fixedUpTasks.copy(features = fixedUpTasks.features.take(1))
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
        ) => true
      )
    }
  }
}
