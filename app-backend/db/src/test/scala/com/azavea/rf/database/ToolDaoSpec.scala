package com.rasterfoundry.database

import doobie.implicits._
import org.scalatest._

class ToolDaoSpec extends FunSuite with Matchers with DBTestConfig {
  test("selection types") {
    xa.use(t => ToolDao.query.list.transact(t))
      .unsafeRunSync
      .length should be >= 0
  }

  // TODO add select and update tests (that will have to test creation)
}
