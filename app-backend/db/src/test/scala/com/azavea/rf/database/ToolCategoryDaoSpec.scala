package com.rasterfoundry.database

import com.rasterfoundry.common.datamodel.{ToolCategory, User}

import doobie.implicits._
import org.scalatest._

class ToolCategoryDaoSpec extends FunSuite with Matchers with DBTestConfig {

  test("types") {
    xa.use(t => ToolCategoryDao.query.list.transact(t))
      .unsafeRunSync
      .length should be >= 0
  }

  test("insertion") {
    val category = "A good, reasonably specific category"
    val makeToolCategory = (user: User) => {
      ToolCategory.Create(category).toToolCategory(user.id)
    }

    val transaction = for {
      user <- defaultUserQ
      toolCategoryIn <- ToolCategoryDao.insertToolCategory(
        makeToolCategory(user),
        user
      )
    } yield (toolCategoryIn)

    val result = xa.use(t => transaction.transact(t)).unsafeRunSync

    result.category shouldBe category
  }
}
