package com.rasterfoundry.database

import com.rasterfoundry.common.Generators.Implicits._
import com.rasterfoundry.datamodel._

import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatestplus.scalacheck.Checkers

class TileLayerDaoSpec
    extends FunSuite
    with Matchers
    with Checkers
    with DBTestConfig
    with PropTestHelpers {
  test("inserting a tile layer") {
    check {
      forAll(
        (
            annotationProjectCreate: AnnotationProject.Create,
            tileLayerCreate: TileLayer.Create
        ) => true
      )
    }
  }

  test("list tile layers") {
    check {
      forAll(
        (
            annotationProjectCreate: AnnotationProject.Create,
            tileLayerCreate: TileLayer.Create
        ) => true
      )
    }
  }
}
