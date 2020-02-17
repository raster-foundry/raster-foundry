package com.rasterfoundry.database

import com.rasterfoundry.common.Generators.Implicits._
import com.rasterfoundry.datamodel._

import doobie.implicits._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatestplus.scalacheck.Checkers

class OrganizationDaoSpec
    extends FunSuite
    with Matchers
    with Checkers
    with DBTestConfig
    with PropTestHelpers {

  // createOrganization
  test("insert an organization from an Organization.Create") {
    check {
      forAll(
        (orgCreate: Organization.Create, platform: Platform) => {
          val orgInsertIO = for {
            insertedPlatform <- PlatformDao.create(platform)
            newOrg <- OrganizationDao.create(
              orgCreate
                .copy(platformId = insertedPlatform.id)
                .toOrganization(true)
            )
          } yield (newOrg, insertedPlatform)
          val (insertedOrg, insertedPlatform) =
            orgInsertIO.transact(xa).unsafeRunSync

          insertedOrg.platformId == insertedPlatform.id &&
          insertedOrg.name == orgCreate.name
        }
      )
    }
  }

  // getOrganizationById
  test("get an organization by id") {
    check {
      forAll(
        (orgCreate: Organization.Create, platform: Platform) => {
          val retrievedNameIO = for {
            dbPlatform <- PlatformDao.create(platform)
            dbOrg <- OrganizationDao.createOrganization(
              orgCreate.copy(platformId = dbPlatform.id)
            )
            fetched <- OrganizationDao.getOrganizationById(dbOrg.id)
          } yield { fetched map { _.name } }

          retrievedNameIO.transact(xa).unsafeRunSync.get == orgCreate.name
        }
      )
    }
  }

  // updateOrganization
  test("update an organization") {
    check {
      forAll(
        (
            orgCreate: Organization.Create,
            newName: String,
            platform: Platform
        ) => {
          val withoutNull = newName.filter(_ != '\u0000').mkString
          val insertAndUpdateIO = for {
            dbPlatform <- PlatformDao.create(platform)
            dbOrg <- OrganizationDao.createOrganization(
              orgCreate.copy(platformId = dbPlatform.id)
            )
            affectedRows <- OrganizationDao
              .update(dbOrg.copy(name = withoutNull), dbOrg.id)
            fetched <- OrganizationDao.unsafeGetOrganizationById(dbOrg.id)
          } yield (affectedRows, fetched.name, fetched.status)

          val (affectedRows, updatedName, updatedStatus) =
            insertAndUpdateIO.transact(xa).unsafeRunSync
          (affectedRows == 1) && (updatedName == withoutNull) && (updatedStatus == orgCreate.status)
        }
      )
    }
  }

  // list organizations
  test("list organizations") {
    OrganizationDao.query.list.transact(xa).unsafeRunSync.length should be >= 0
  }

  test("list authorized organizations") {
    check {
      forAll(
        (
            uc1: User.Create,
            uc2: User.Create,
            uc3: User.Create,
            pc1: Platform,
            pc2: Platform,
            org1: Organization.Create,
            org2: Organization.Create,
            org3: Organization.Create
        ) => {
          val defaultUser = uc1.toUser.copy(id = "default")
          val orgsIO = for {
            p1 <- PlatformDao.create(pc1)
            p2 <- PlatformDao.create(pc2)
            org1 <- OrganizationDao.createOrganization(
              org1
                .copy(platformId = p1.id, visibility = Some(Visibility.Private))
            )
            org2 <- OrganizationDao.createOrganization(
              org2
                .copy(platformId = p1.id, visibility = Some(Visibility.Public))
            )
            org3 <- OrganizationDao.createOrganization(
              org3
                .copy(platformId = p2.id, visibility = Some(Visibility.Public))
            )
            u1 <- UserDao.create(uc1)
            u2 <- UserDao.create(uc2)
            u3 <- UserDao.create(uc3)
            _ <- UserGroupRoleDao.create(
              UserGroupRole
                .Create(u1.id, GroupType.Platform, p1.id, GroupRole.Member)
                .toUserGroupRole(defaultUser, MembershipStatus.Approved)
            )
            _ <- UserGroupRoleDao.create(
              UserGroupRole
                .Create(
                  u1.id,
                  GroupType.Organization,
                  org1.id,
                  GroupRole.Member
                )
                .toUserGroupRole(defaultUser, MembershipStatus.Approved)
            )
            u2platugr <- UserGroupRoleDao.create(
              UserGroupRole
                .Create(u2.id, GroupType.Platform, p1.id, GroupRole.Member)
                .toUserGroupRole(defaultUser, MembershipStatus.Approved)
            )
            _ <- UserGroupRoleDao.create(
              UserGroupRole
                .Create(
                  u2.id,
                  GroupType.Organization,
                  org2.id,
                  GroupRole.Member
                )
                .toUserGroupRole(defaultUser, MembershipStatus.Approved)
            )
            _ <- UserGroupRoleDao.create(
              UserGroupRole
                .Create(u3.id, GroupType.Platform, p2.id, GroupRole.Member)
                .toUserGroupRole(defaultUser, MembershipStatus.Approved)
            )
            _ <- UserGroupRoleDao.create(
              UserGroupRole
                .Create(
                  u3.id,
                  GroupType.Organization,
                  org3.id,
                  GroupRole.Member
                )
                .toUserGroupRole(defaultUser, MembershipStatus.Approved)
            )
            u1VisibleOrgs <- OrganizationDao.viewFilter(u1).list
            u2VisibleOrgs <- OrganizationDao.viewFilter(u2).list
            u3VisibleOrgs <- OrganizationDao.viewFilter(u3).list
            _ <- UserGroupRoleDao
              .update(u2platugr.copy(groupRole = GroupRole.Admin), u2platugr.id)
            u2AdminVisibleOrgs <- OrganizationDao.viewFilter(u2).list
          } yield {
            (
              org1,
              org2,
              org3,
              u1VisibleOrgs,
              u2VisibleOrgs,
              u3VisibleOrgs,
              u2AdminVisibleOrgs
            )
          }

          val (
            hiddenOrg,
            visibleOrg,
            otherPlatformOrg,
            u1orgs,
            u2orgs,
            u3orgs,
            u2orgsAdmin
          ) = orgsIO.transact(xa).unsafeRunSync
          val u1orgids = u1orgs.toSet.map { o: Organization =>
            o.id
          }
          val u2orgids = u2orgs.toSet.map { o: Organization =>
            o.id
          }
          val u3orgids = u3orgs.toSet.map { o: Organization =>
            o.id
          }
          val u2orgidsAdmin = u2orgsAdmin.toSet.map { o: Organization =>
            o.id
          }
          assert(
            u1orgids.contains(hiddenOrg.id),
            "; members of private orgs should be able to see it"
          )
          assert(
            !u1orgids.contains(otherPlatformOrg.id) &&
              !u2orgids.contains(otherPlatformOrg.id) &&
              !u3orgids.contains(visibleOrg.id),
            "; members should not be able to view orgs on other platforms"
          )
          assert(
            u1orgids.contains(visibleOrg.id) &&
              u2orgids.contains(visibleOrg.id) &&
              u3orgids.contains(otherPlatformOrg.id),
            "; members should be able to view public orgs on their own platform"
          )
          assert(
            u2orgidsAdmin.contains(hiddenOrg.id),
            "; platform admins should be able to see hidden orgs"
          )
          true
        }
      )
    }
  }

  test("add a user role") {
    check {
      forAll {
        (
            platform: Platform,
            userCreate: User.Create,
            orgCreate: Organization.Create,
            userRole: GroupRole
        ) =>
          {
            val addPlatformRoleWithPlatformIO = for {
              (dbUser, dbOrg, _) <- insertUserOrgPlatform(
                userCreate,
                orgCreate,
                platform,
                false
              )
              insertedUserGroupRole <- OrganizationDao.addUserRole(
                dbOrg.platformId,
                dbUser,
                dbUser.id,
                dbOrg.id,
                userRole
              )
              byIdUserGroupRole <- UserGroupRoleDao.getOption(
                insertedUserGroupRole.id
              )
            } yield { (dbOrg, byIdUserGroupRole) }

            val (dbOrg, dbUserGroupRole) =
              addPlatformRoleWithPlatformIO.transact(xa).unsafeRunSync
            dbUserGroupRole match {
              case Some(ugr) =>
                assert(
                  ugr.isActive,
                  "; Added role should be active"
                )
                assert(
                  ugr.groupType == GroupType.Organization,
                  "; Added role should be for an Organization"
                )
                assert(
                  ugr.groupId == dbOrg.id,
                  "; Added role should be for the correct Organization"
                )
                assert(
                  ugr.groupRole == userRole,
                  "; Added role should have the correct role"
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
      forAll {
        (
            platform: Platform,
            userCreate: User.Create,
            orgCreate: Organization.Create,
            userRole: GroupRole
        ) =>
          {
            val setPlatformRoleIO = for {
              (dbUser, dbOrg, _) <- insertUserOrgPlatform(
                userCreate,
                orgCreate,
                platform,
                false
              )
              originalUserGroupRole <- OrganizationDao.addUserRole(
                dbOrg.platformId,
                dbUser,
                dbUser.id,
                dbOrg.id,
                userRole
              )
              updatedUserGroupRoles <- OrganizationDao.deactivateUserRoles(
                dbUser.id,
                dbOrg.id
              )
            } yield { (originalUserGroupRole, updatedUserGroupRoles) }

            val (dbOldUGR, dbUpdatedUGRs) =
              setPlatformRoleIO.transact(xa).unsafeRunSync

            assert(dbUpdatedUGRs.size === 1, "; A single UGR should be updated")
            assert(
              dbUpdatedUGRs.filter((ugr) => ugr.isActive == true).size == 0,
              "; There should be no active UGRs"
            )
            assert(
              dbUpdatedUGRs
                .filter((ugr) => ugr.isActive == false)
                .size == dbUpdatedUGRs.size,
              "; The updated  UGRs should all be deactivated"
            )
            assert(
              dbUpdatedUGRs
                .filter((ugr) => ugr.id == dbOldUGR.id && ugr.isActive == false)
                .size == 1,
              "; The originally created UGR should be updated to be inactive"
            )
            true
          }
      }
    }
  }
}
