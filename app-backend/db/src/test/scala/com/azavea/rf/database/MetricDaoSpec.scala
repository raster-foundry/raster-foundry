package com.rasterfoundry.database

import com.rasterfoundry.common.Generators.Implicits._
import com.rasterfoundry.datamodel.Metric

import cats.implicits._
import doobie.implicits._
import org.scalacheck.Prop.forAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.Checkers

import scala.util.Random

class MetricDaoSpec
    extends AnyFunSuite
    with Matchers
    with Checkers
    with DBTestConfig
    with PropTestHelpers {
  // 0.8 cutoff means it's about 50/50 whether we get three repetitions
  // 3 seemed like a nice number to expect on average
  def getRepetitionAttempts(init: Int): Int =
    if (Random.nextFloat > 0.8) init else getRepetitionAttempts(init + 1)
  test("insert and update a metric") {
    check {
      forAll { (metric: Metric) =>
        {
          val repetitions = getRepetitionAttempts(0)
          val metricIO = for {
            _ <- MetricDao.insert(metric)
            countOnce <- MetricDao.unsafeGetMetric(metric)
            _ <- List.fill(repetitions)(()) traverse { _ =>
              MetricDao.insert(metric)
            }
            countAgain <- MetricDao.unsafeGetMetric(metric)
          } yield { (countOnce, countAgain) }

          val (initial, afterUpdate) = metricIO.transact(xa).unsafeRunSync
          assert(initial.value == 1,
                 "On insert, the count for this metric should be 1")
          assert(
            afterUpdate.value == 1 + repetitions,
            "After updating, the count for this metric should be 1 + the number of updates")
          true
        }
      }
    }
  }
}
