package com.azavea.rf.database

import com.azavea.rf.datamodel.{ToolCategory, User}
import com.azavea.rf.database.Implicits._

import doobie._, doobie.implicits._
import cats._, cats.data._, cats.effect.IO
import cats.syntax.either._
import doobie.postgres._, doobie.postgres.implicits._
import doobie.scalatest.imports._
import org.scalatest._


class ToolCategoryDaoSpec extends FunSuite with Matchers with IOChecker with DBTestConfig {

  test("types") { check(ToolCategoryDao.selectF.query[ToolCategory]) }

  test("insertion") {
    val category = "A good, reasonably specific category"
    val makeToolCategory = (user : User) => {
      ToolCategory.Create(category).toToolCategory(user.id)
    }

    val transaction = for {
      user <- defaultUserQ
      toolCategoryIn <- ToolCategoryDao.insertToolCategory(
        makeToolCategory(user), user
      )
    } yield (toolCategoryIn)

    val result = transaction.transact(xa).unsafeRunSync

    result.category shouldBe category
  }
}

