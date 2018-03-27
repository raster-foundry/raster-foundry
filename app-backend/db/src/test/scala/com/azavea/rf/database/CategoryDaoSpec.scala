package com.azavea.rf.database

import com.azavea.rf.datamodel.{Category, User}
import com.azavea.rf.database.Implicits._

import doobie._, doobie.implicits._
import cats._, cats.data._, cats.effect.IO
import cats.syntax.either._
import doobie.postgres._, doobie.postgres.implicits._
import doobie.scalatest.imports._
import org.scalatest._


class CategoryDaoSpec extends FunSuite with Matchers with IOChecker with DBTestConfig {

  test("types") { check(CategoryDao.selectF.query[Category]) }

  test("insertion") {
    val category = "A good, reasonably specific category"
    val makeCategory = (user : User) => {
      Category.Create(category).toCategory(user.id)
    }

    val transaction = for {
      user <- defaultUserQ
      categoryIn <- CategoryDao.insertCategory(
        makeCategory(user), user
      )
    } yield (categoryIn)

    val result = transaction.transact(xa).unsafeRunSync

    result.category shouldBe category
  }
}

