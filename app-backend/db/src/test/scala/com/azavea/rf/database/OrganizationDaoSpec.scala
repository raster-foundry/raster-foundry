package com.azavea.rf.database

import com.azavea.rf.datamodel._
import com.azavea.rf.datamodel.Generators.Implicits._
import com.azavea.rf.database.Implicits._

import cats._, cats.data._, cats.effect.IO
import cats.syntax.either._
import doobie._, doobie.implicits._
import doobie.postgres._, doobie.postgres.implicits._
import doobie.scalatest.imports._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatest.prop.Checkers


class OrganizationDaoSpec extends FunSuite with Matchers with Checkers with DBTestConfig with PropTestHelpers {

  // createOrganization
  test("insert an organization from an Organization.Create") {
    check {
      forAll(
        (rootUserCreate: User.Create, orgCreate: Organization.Create, platform: Platform) => {
          val orgInsertIO = for {
            rootOrg <- rootOrgQ
            insertedUser <- UserDao.create(rootUserCreate)
            insertedPlatform <- PlatformDao.create(platform)
            newOrg <- OrganizationDao.create(orgCreate.copy(platformId = insertedPlatform.id).toOrganization)
          } yield (newOrg, insertedPlatform)
          val (insertedOrg, insertedPlatform) = orgInsertIO.transact(xa).unsafeRunSync

          insertedOrg.platformId == insertedPlatform.id &&
            insertedOrg.name == orgCreate.name &&
            insertedOrg.isActive == true
        }
      )
    }
  }

  // getOrganizationById
  test("get an organization by id") {
    check {
      forAll(
        (orgCreate: Organization.Create) => {
          val retrievedNameIO = OrganizationDao.createOrganization(orgCreate) flatMap {
            (org: Organization) => {
              OrganizationDao.getOrganizationById(org.id) map {
                (retrievedO: Option[Organization]) => retrievedO map { _.name }
              }
            }
          }
          retrievedNameIO.transact(xa).unsafeRunSync.get == orgCreate.name
        }
      )
    }
  }

  // updateOrganization
  test("update an organization") {
    check {
      forAll(
        (orgCreate: Organization.Create, newName: String) => {
          val withoutNull = newName.filter( _ != '\u0000' ).mkString
          val insertOrgIO = OrganizationDao.createOrganization(orgCreate)
          val insertAndUpdateIO =  insertOrgIO flatMap {
            (org: Organization) => {
              OrganizationDao.update(org.copy(name = withoutNull), org.id) flatMap {
                case (affectedRows: Int) => {
                  OrganizationDao.unsafeGetOrganizationById(org.id) map {
                    (retrievedOrg: Organization) => (affectedRows, retrievedOrg.name, retrievedOrg.isActive)
                  }
                }
              }
            }
          }
          val (affectedRows, updatedName, updatedActive) = insertAndUpdateIO.transact(xa).unsafeRunSync
          (affectedRows == 1) && (updatedName == withoutNull) && (updatedActive == true)
        }
      )
    }
  }

  // list organizations
  test("list organizations") {
    OrganizationDao.query.list.transact(xa).unsafeRunSync.length should be >= 0
  }

  test("add a user role") {
    check {
      forAll{
        (userCreate: User.Create, orgCreate: Organization.Create, userRole: GroupRole) => {
          val addPlatformRoleWithPlatformIO = for {
            orgAndUser <- insertUserAndOrg(userCreate, orgCreate)
                                          (org, dbUser) = orgAndUser
            insertedUserGroupRole <- OrganizationDao.addUserRole(dbUser, dbUser.id, org.id, userRole)
            byIdUserGroupRole <- UserGroupRoleDao.getOption(insertedUserGroupRole.id)
          } yield { (org, byIdUserGroupRole) }

          val (dbOrg, dbUserGroupRole) = addPlatformRoleWithPlatformIO.transact(xa).unsafeRunSync
          dbUserGroupRole match {
            case Some(ugr) =>
              assert(
                ugr.isActive, "; Added role should be active"
              )
              assert(
                ugr.groupType == GroupType.Organization, "; Added role should be for an Organization"
              )
              assert(
                ugr.groupId == dbOrg.id, "; Added role should be for the correct Organization"
              )
              assert(
                ugr.groupRole == userRole, "; Added role should have the correct role"
              )
              true
            case _ => false
          }
        }
      }
    }
  }

  test("deactivate a user's roles") {
    check {
      forAll{
        (
          userCreate: User.Create, orgCreate: Organization.Create, userRole: GroupRole
        ) => {
          val setPlatformRoleIO = for {
            orgAndUser <- insertUserAndOrg(userCreate, orgCreate, false)
            (org, dbUser) = orgAndUser
            originalUserGroupRole <- OrganizationDao.addUserRole(dbUser, dbUser.id, org.id, userRole)
            updatedUserGroupRoles <- OrganizationDao.deactivateUserRoles(dbUser, dbUser.id, org.id)
          } yield { (originalUserGroupRole, updatedUserGroupRoles) }

          val (dbOldUGR, dbUpdatedUGRs) = setPlatformRoleIO.transact(xa).unsafeRunSync

          assert(dbUpdatedUGRs.size === 1, "; A single UGR should be updated")
          assert(dbUpdatedUGRs.filter((ugr) => ugr.isActive == true).size == 0,
                 "; There should be no active UGRs")
          assert(dbUpdatedUGRs.filter((ugr) => ugr.isActive == false).size == dbUpdatedUGRs.size,
                 "; The updated  UGRs should all be deactivated")
          assert(dbUpdatedUGRs.filter((ugr) => ugr.id == dbOldUGR.id && ugr.isActive == false).size == 1,
                 "; The originally created UGR should be updated to be inactive")
          true
        }
      }
    }
  }

  test("replace a user's roles") {
    check {
      forAll{
        (
          userCreate: User.Create, orgCreate: Organization.Create, userRole: GroupRole
        ) => {
          val setPlatformRoleIO = for {
            orgAndUser <- insertUserAndOrg(userCreate, orgCreate, false)
            (org, dbUser) = orgAndUser
            originalUserGroupRole <- OrganizationDao.addUserRole(dbUser, dbUser.id, org.id, userRole)
            updatedUserGroupRoles <- OrganizationDao.setUserRole(dbUser, dbUser.id, org.id, userRole)
            allUserGroupRoles <- UserGroupRoleDao.query.filter(fr"user_id = ${dbUser.id}").list
          } yield { (org, originalUserGroupRole, updatedUserGroupRoles, allUserGroupRoles ) }

          val (dbOrg, dbOldUGR, dbUpdatedUGRs, dbAllUGRs) = setPlatformRoleIO.transact(xa).unsafeRunSync

          assert(dbUpdatedUGRs.filter((ugr) => ugr.isActive == true).size == 2,
                 "; There should be two active roles in the updated roles, " +
                   "one for the platform and one for the org")
          assert(dbUpdatedUGRs.filter((ugr) => ugr.isActive == false).size == 1,
                 "; If an active role exists, it should be updated to be inactive")
          assert(dbUpdatedUGRs.filter((ugr) => ugr.id == dbOldUGR.id && ugr.isActive == false).size == 1,
                 "; The old role should be updated to be inactive")
          assert(dbUpdatedUGRs.filter((ugr) => ugr.id != dbOldUGR.id && ugr.isActive == true).size == 2,
                 "; The new roles (org and platform) should be active")
          assert(dbAllUGRs.size == 3, "; The user should, in total, have 3 roles specified")
          assert(dbUpdatedUGRs.size == 3, "; Expected 2 new active roles, and one deactivated role")
          true
        }
      }
    }
  }

  test("change a user's organization") {
    check {
      forAll{
        (userCreate: User.Create, oldUserOrg: Organization.Create, oldUserGroupRole: GroupRole,
         newUserOrg: Organization.Create, newUserGroupRole: GroupRole, platform: Platform) => {
          val changeOrgIO = for {
            insertedPlatform <- PlatformDao.create(platform)
            dbNewOrg <- OrganizationDao.create(
              newUserOrg.copy(platformId = insertedPlatform.id).toOrganization
            )
            orgAndUser <- insertUserAndOrg(
              userCreate, oldUserOrg.copy(platformId = insertedPlatform.id), false
            )
            (dbOldOrg, dbUser) = orgAndUser
            originalUserGroupRoles <- OrganizationDao.addUserRole(dbUser, dbUser.id, dbOldOrg.id, oldUserGroupRole)
            updatedUserGroupRoles <- OrganizationDao.setUserOrganization(
              dbUser, dbUser.id, dbNewOrg.id, newUserGroupRole
            )
            allUserGroupRoles <- UserGroupRoleDao.query.filter(fr"user_id = ${dbUser.id}").list
          } yield { (dbOldOrg, dbNewOrg, originalUserGroupRoles, allUserGroupRoles) }
          val (oldOrg, newOrg, oldRoles, newRoles) = changeOrgIO.transact(xa).unsafeRunSync
          assert(newRoles.filter((ugr) => ugr.isActive == true).size == 2,
                 "; There should be two active roles after changing orgs - platform and the new org"
                 )
          assert(newRoles.filter((ugr) => ugr.isActive == false).size == 1,
                 "; There should be 1 inactive role after changing orgs - the old org"
          )
          assert(newRoles.size == 3, "; The user should have a total of 3 roles, one inactive and 2 active")
          true
        }

      }
    }
  }

}
