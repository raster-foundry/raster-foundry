package com.azavea.rf.database

import com.azavea.rf.datamodel.{Organization, Platform, User, GroupRole, GroupType}
import com.azavea.rf.datamodel.Generators.Implicits._
import com.azavea.rf.database.Implicits._
import doobie._
import doobie.implicits._
import cats._
import cats.data._
import cats.effect.IO
import cats.syntax.either._
import doobie.postgres._
import doobie.postgres.implicits._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatest.prop.Checkers
import io.circe._
import io.circe.syntax._
import com.lonelyplanet.akka.http.extensions.PageRequest
import java.util.UUID

class PlatformDaoSpec extends FunSuite with Matchers with Checkers with DBTestConfig with PropTestHelpers {

  test("list platforms") {
    check {
      forAll {
        (pageRequest: PageRequest) => {
          PlatformDao.listPlatforms(pageRequest).transact(xa).unsafeRunSync.results.length >= 0
        }
      }
    }
  }

  test("insert a platform") {
    check {
      forAll {
        (userCreate: User.Create, orgCreate: Organization.Create, platform: Platform) => {
          val insertPlatformIO = for {
            orgAndUser <- insertUserAndOrg(userCreate, orgCreate)
            (_, dbUser) = orgAndUser
            insertedPlatform <- PlatformDao.create(platform)
          } yield { insertedPlatform }

          val dbPlatform = insertPlatformIO.transact(xa).unsafeRunSync

          dbPlatform.name == platform.name &&
            dbPlatform.settings == platform.settings
        }
      }
    }
  }

  test("update a platform") {
    check {
      forAll {
        (userCreate: User.Create, orgCreate: Organization.Create, platform: Platform, platformUpdate: Platform) => {
          val insertPlatformWithUserIO = for {
            orgAndUser <- insertUserAndOrg(userCreate, orgCreate)
            (_, dbUser) = orgAndUser
            insertedPlatform <- PlatformDao.create(platform)
          } yield { (insertedPlatform, dbUser) }

          val updatePlatformWithPlatformAndAffectedRowsIO = insertPlatformWithUserIO flatMap {
            case (dbPlatform: Platform, dbUser: User) => {
              PlatformDao.update(platformUpdate, dbPlatform.id, dbUser) flatMap {
                (affectedRows: Int) => {
                  PlatformDao.unsafeGetPlatformById(dbPlatform.id) map { (affectedRows, _) }
                }
              }
            }
          }

        val (affectedRows, updatedPlatform) = updatePlatformWithPlatformAndAffectedRowsIO.transact(xa).unsafeRunSync
          affectedRows == 1 &&
            updatedPlatform.name == platformUpdate.name &&
            updatedPlatform.settings == platformUpdate.settings
        }
      }
    }
  }

  test("delete a platform") {
    check {
      forAll {
        (userCreate: User.Create, orgCreate: Organization.Create, platform: Platform) => {
          val deletePlatformWithPlatformIO = for {
            orgAndUser <- insertUserAndOrg(userCreate, orgCreate)
            (_, dbUser) = orgAndUser
            insertedPlatform <- PlatformDao.create(platform)
            deletePlatform <- PlatformDao.delete(insertedPlatform.id)
            byIdPlatform <- PlatformDao.getPlatformById(insertedPlatform.id)
          } yield { (deletePlatform, byIdPlatform) }

          val (rowsAffected, platformById) = deletePlatformWithPlatformIO.transact(xa).unsafeRunSync
          rowsAffected == 1 && platformById == None
        }
      }
    }
  }

  test("add a user role") {
    check {
      forAll{
        (
          userCreate: User.Create, orgCreate: Organization.Create, platform: Platform,
          userRole: GroupRole
        ) => {
          val addPlatformRoleWithPlatformIO = for {
            orgAndUser <- insertUserAndOrg(userCreate, orgCreate)
                                          (org, dbUser) = orgAndUser
            insertedPlatform <- PlatformDao.create(platform)
            insertedUserGroupRole <- PlatformDao.addUserRole(dbUser, dbUser, insertedPlatform.id, userRole)
            byIdUserGroupRole <- UserGroupRoleDao.getOption(insertedUserGroupRole.id)
          } yield { (insertedPlatform, byIdUserGroupRole) }

          val (dbPlatform, dbUserGroupRole) = addPlatformRoleWithPlatformIO.transact(xa).unsafeRunSync
          dbUserGroupRole match {
            case Some(ugr) =>
              assert(ugr.isActive, "; Added role should be active")
              assert(ugr.groupType == GroupType.Platform, "; Added role should be for a Platform")
              assert(ugr.groupId == dbPlatform.id, "; Added role should be for the correct Platform")
              assert(ugr.groupRole == userRole, "; Added role should have the correct role")
              true
            case _ => false
          }
        }
      }
    }
  }

  test("replace a user's roles") {
    check {
      forAll{
        (
          userCreate: User.Create, orgCreate: Organization.Create, platform: Platform,
          userRole: GroupRole
        ) => {
          val setPlatformRoleIO = for {
            orgAndUser <- insertUserAndOrg(userCreate, orgCreate)
            (org, dbUser) = orgAndUser
            insertedPlatform <- PlatformDao.create(platform)
            originalUserGroupRole <- PlatformDao.addUserRole(dbUser, dbUser, insertedPlatform.id, userRole)
            updatedUserGroupRoles <- PlatformDao.setUserRole(dbUser, dbUser, insertedPlatform.id, userRole)
          } yield { (insertedPlatform, originalUserGroupRole, updatedUserGroupRoles ) }

          val (dbPlatform, dbOldUGR, dbNewUGRs) = setPlatformRoleIO.transact(xa).unsafeRunSync

          assert(dbNewUGRs.filter((ugr) => ugr.isActive == true).size == 1,
                 "; Updated UGRs should have one set to active")
          assert(dbNewUGRs.filter((ugr) => ugr.isActive == false).size == 1,
                 "; Updated UGRs should have one set to inactive")
          assert(dbNewUGRs.filter((ugr) => ugr.id == dbOldUGR.id && ugr.isActive == false).size == 1,
                 "; Old UGR should be set to inactive")
          assert(dbNewUGRs.filter((ugr) => ugr.id != dbOldUGR.id && ugr.isActive == true).size == 1,
                 "; New UGR should be set to active")
          assert(dbNewUGRs.size == 2, "; Update should have old and new UGRs")
          true
        }
      }
    }
  }

  test("deactivate a user's roles") {
    check {
      forAll{
        (
          userCreate: User.Create, orgCreate: Organization.Create, platform: Platform,
          userRole: GroupRole
        ) => {
          val setPlatformRoleIO = for {
            orgAndUser <- insertUserAndOrg(userCreate, orgCreate)
            (org, dbUser) = orgAndUser
            insertedPlatform <- PlatformDao.create(platform)
            originalUserGroupRole <- PlatformDao.addUserRole(dbUser, dbUser, insertedPlatform.id, userRole)
            updatedUserGroupRoles <- PlatformDao.deactivateUserRoles(dbUser, dbUser, insertedPlatform.id)
          } yield { (insertedPlatform, originalUserGroupRole, updatedUserGroupRoles ) }

          val (dbPlatform, dbOldUGR, dbNewUGRs) = setPlatformRoleIO.transact(xa).unsafeRunSync

          assert(dbNewUGRs.filter((ugr) => ugr.isActive == false).size == 1,
                 "; The updated UGR should be inactive")
          assert(dbNewUGRs.size == 1, "; There should only be a single UGR updated")
          true
        }
      }
    }
  }
}
