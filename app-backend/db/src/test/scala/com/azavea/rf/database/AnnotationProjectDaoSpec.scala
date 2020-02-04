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
        (userCreate: User.Create,
          annotationProjectCreate: AnnotationProject.Create) => {
          val insertIO = for {
            user <- UserDao.create(userCreate)
            inserted <- AnnotationProjectDao.insertAnnotationProject(annotationProjectCreate, user)
          } yield inserted

          insertIO.transact(xa).map(_ => true).unsafeRunSync
        }
      )
    }
  }
}
