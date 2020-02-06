package com.rasterfoundry.database

import com.rasterfoundry.datamodel._
import com.rasterfoundry.common.Generators.Implicits._
import doobie.implicits._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatestplus.scalacheck.Checkers

class AnnotationProjectDaoSpec
    extends FunSuite
    with Matchers
    with Checkers
    with DBTestConfig
    with PropTestHelpers {
  test("inserting an annotation project") {
    check {
      forAll(
        (
            userCreate: User.Create,
            annotationProjectCreate: AnnotationProject.Create
        ) => {
          val insertIO = for {
            user <- UserDao.create(userCreate)
            inserted <- AnnotationProjectDao
              .insertAnnotationProject(annotationProjectCreate, user)
          } yield inserted

          val result = insertIO.transact(xa).unsafeRunSync

          assert(
            result.tileLayers.length == annotationProjectCreate.tileLayers.length,
            "All the tile layers were inserted"
          )
          assert(
            result.labelClassGroups.length == annotationProjectCreate.labelClassGroups.length,
            "All the annotation class groups were inserted"
          )
          assert(
            result.name == annotationProjectCreate.name &&
              result.projectType == annotationProjectCreate.projectType &&
              result.taskSizeMeters == annotationProjectCreate.taskSizeMeters &&
              result.aoi == annotationProjectCreate.aoi &&
              result.labelersTeamId == annotationProjectCreate.labelersTeamId &&
              result.validatorsTeamId == annotationProjectCreate.validatorsTeamId &&
              result.projectId == annotationProjectCreate.projectId,
            "Created project respects data from project to create"
          )

          true
        }
      )
    }
  }
}
