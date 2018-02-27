package com.azavea.rf.database

import com.azavea.rf.datamodel.{Organization, User, UserRole, UserRoleRole, Viewer, Admin}
import com.azavea.rf.database.meta.RFMeta._

import doobie._, doobie.implicits._
import cats._, cats.data._, cats.effect.IO
import cats.syntax.either._
import doobie.postgres._, doobie.postgres.implicits._
import doobie.scalatest.imports._
import org.scalatest._

import scala.util.Random


class UserDaoSpec extends FunSuite with Matchers with IOChecker with DBTestConfig {

  test("types") { check(UserDao.selectF.query[User]) }

  test("insertion") {
    val makeUser = (org: Organization, role: UserRole) => {
      val userId = Random.nextString(10)
      User.Create(userId, org.id, role)
    }
    val transaction = for {
      org <- rootOrgQ
      user = makeUser(org, UserRoleRole)
      viewer = makeUser(org, Viewer)
      admin = makeUser(org, Admin)
      userIn <- UserDao.create(user)
      viewerIn <- UserDao.create(viewer)
      adminIn <- UserDao.create(admin)
    } yield (userIn, viewerIn, adminIn)

    val (userResult, viewerResult, adminResult) =
      transaction.transact(xa).unsafeRunSync

    userResult.role shouldBe UserRoleRole
    viewerResult.role shouldBe Viewer
    adminResult.role shouldBe Admin
  }
}

