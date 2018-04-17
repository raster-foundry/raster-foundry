package com.azavea.rf.database

import com.azavea.rf.datamodel.{UserGroupRole, GroupType, GroupRole}
import com.azavea.rf.database.Implicits._
import doobie._
import doobie.implicits._
import cats._
import cats.data._
import cats.effect.IO
import cats.syntax.either._
import doobie.postgres._
import doobie.postgres.implicits._
import doobie.scalatest.imports._
import org.scalatest._
import io.circe._
import io.circe.syntax._
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global

class UserGroupRoleDaoSpec extends FunSuite with Matchers with IOChecker with DBTestConfig {

    def createUserGroupRole: ConnectionIO[UserGroupRole] = {
        for {
            platform <- defaultPlatformQ
            usr <- defaultUserQ
            create <- {
                val ugr = UserGroupRole.Create(
                    usr,
                    GroupType.Platform,
                    platform.id,
                    GroupRole.Member,
                    usr
                )
                UserGroupRoleDao.create(ugr.toUserGroupRole)
            }
        } yield create
    }

    test("read types") { check(UserGroupRoleDao.selectF.query[UserGroupRole]) }

    test("insertion") {
        val transaction = for {
            ugrIn <- createUserGroupRole
            ugrOut <- UserGroupRoleDao.getOption(ugrIn.id)
        } yield ugrOut

        val result = transaction.transact(xa).unsafeRunSync
        result.get.groupType shouldBe GroupType.Platform
    }

    test("update") {
        val transaction = for {
            usr <- defaultUserQ
            ugrIn <- createUserGroupRole
            ugrUpdate <- UserGroupRoleDao.update(ugrIn.copy(groupRole = GroupRole.Admin), ugrIn.id, usr)
            ugrOut <- UserGroupRoleDao.getOption(ugrIn.id)
        } yield ugrOut

        val result = transaction.transact(xa).unsafeRunSync
        result.get.groupRole shouldBe GroupRole.Admin
    }

    test("deactivation") {
        val transaction = for {
            usr <- defaultUserQ
            ugrIn <- createUserGroupRole
            ugrDeactivated <- UserGroupRoleDao.deactivate(ugrIn.id, usr)
            ugrOut <- UserGroupRoleDao.getOption(ugrIn.id)
        } yield ugrOut

        val result = transaction.transact(xa).unsafeRunSync
        result.get.isActive shouldBe false
    }
}
