package com.rasterfoundry.database

import com.rasterfoundry.datamodel.{Order, PageRequest, TaskSessionType}

import org.scalatest.funsuite.AnyFunSuite

import java.util.UUID

class CampaignPerformanceSpec
    extends AnyFunSuite
    with doobie.scalatest.IOChecker
    with DBTestConfig {

  val transactor = xa

  val campaignId: UUID = UUID.randomUUID

  test("performance query is valid for label sessions") {
    check {
      CampaignDao.performanceQ(
        campaignId,
        TaskSessionType.LabelSession,
        PageRequest(
          0,
          10,
          Map(
            "hoursSpent" -> Order.Asc,
            "tasksCompleted" -> Order.Desc,
            "labellingRate" -> Order.Asc
          )
        )
      )
    }
  }

  test("performance query is valid for validation sessions") {
    check {
      CampaignDao.performanceQ(
        campaignId,
        TaskSessionType.ValidateSession,
        PageRequest(
          0,
          10,
          Map(
            "hoursSpent" -> Order.Asc,
            "tasksCompleted" -> Order.Desc,
            "validationRate" -> Order.Asc
          )
        )
      )
    }
  }

  test("unique users query is valid") {
    check {
      CampaignDao.uniqueUsersQ(campaignId)
    }
  }

}
