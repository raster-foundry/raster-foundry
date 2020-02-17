package com.rasterfoundry.database

import com.rasterfoundry.datamodel._
import com.rasterfoundry.common.Generators.Implicits._
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
            annotationCreates: List[AnnotationLabelWithClasses.Create]
        ) => true
      )
    }
  }

  test("list labels for a project") {
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
