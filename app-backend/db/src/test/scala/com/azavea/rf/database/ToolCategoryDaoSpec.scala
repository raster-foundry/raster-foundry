package com.rasterfoundry.database

import com.rasterfoundry.common.datamodel.{ToolCategory, User}
import com.rasterfoundry.database.Implicits._

import doobie._, doobie.implicits._
import cats._, cats.data._, cats.effect.IO
import cats.syntax.either._
import doobie.postgres._, doobie.postgres.implicits._
import doobie.scalatest.imports._
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
