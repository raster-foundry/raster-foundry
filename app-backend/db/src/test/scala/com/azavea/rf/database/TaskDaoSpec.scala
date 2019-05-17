package com.rasterfoundry.database

import com.rasterfoundry.datamodel._
import com.rasterfoundry.common.Generators.Implicits._
import com.rasterfoundry.database.Implicits._
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
  test("insert a task from a task feature create") {
    check {
      forAll { (user: User.Create, taskCreate: Task.TaskFeatureCreate) =>
        {
          logger.info("Yup running a test")
          !user.id.isEmpty && !taskCreate.geometry.isEmpty
        }
      }
    }
  }
}
