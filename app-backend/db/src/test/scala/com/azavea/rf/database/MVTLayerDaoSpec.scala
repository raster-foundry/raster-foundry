package com.rasterfoundry.database

import org.scalatest.funsuite.AnyFunSuite

import java.util.UUID

class MVTLayerDaoSpec
    extends AnyFunSuite
    with doobie.scalatest.IOChecker
    with DBTestConfig {

  val transactor = xa

  val mockAnnotationProjectId = UUID.randomUUID

  test("labels mvt query is valid") {
    check(
      MVTLayerDao.getAnnotationProjectLabelsQ(mockAnnotationProjectId, 0, 0, 0)
    )
  }

  test("tasks mvt query is valid") {
    check(
      MVTLayerDao.getAnnotationProjectTasksQ(mockAnnotationProjectId, 0, 0, 0)
    )
  }

}
