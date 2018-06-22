package com.azavea.rf.database

import com.azavea.rf.datamodel._
import com.azavea.rf.datamodel.Generators.Implicits._
import com.azavea.rf.database.Implicits._
import doobie._
import doobie.implicits._
import cats._
import cats.data._
import cats.effect.IO
import cats.implicits._
import cats.syntax.either._
import com.lonelyplanet.akka.http.extensions.PageRequest
import doobie.postgres._
import doobie.postgres.implicits._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatest.prop.Checkers
import io.circe._
import io.circe.syntax._
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global

class UserGroupRoleDaoSpec extends FunSuite with Matchers with Checkers with DBTestConfig with PropTestHelpers {

  test("list user group roles") {
    UserGroupRoleDao.query.list.transact(xa).unsafeRunSync.length should be >= 0
  }

  test("insert a user group role") {
    check {
      forAll {
        (userCreate: User.Create, orgCreate: Organization.Create, teamCreate: Team.Create,
         platform: Platform, ugrCreate: UserGroupRole.Create) => {
          val insertUgrIO = for {
            orgAndUser <- insertUserAndOrg(userCreate, orgCreate, false)
            (insertedOrg, insertedUser) = orgAndUser
            insertedTeam <- TeamDao.create(teamCreate.copy(organizationId = insertedOrg.id).toTeam(insertedUser))
            insertedPlatform <- PlatformDao.create(platform)
            insertedUgr <- UserGroupRoleDao.create(
              fixupUserGroupRole(insertedUser, insertedOrg, insertedTeam, insertedPlatform, ugrCreate)
                .toUserGroupRole(insertedUser, MembershipStatus.Approved)
            )
          } yield { insertedUgr }

          val insertedUgr = insertUgrIO.transact(xa).unsafeRunSync

          insertedUgr.userId == userCreate.id &&
            insertedUgr.groupType == ugrCreate.groupType &&
            insertedUgr.groupRole == ugrCreate.groupRole &&
            insertedUgr.modifiedBy == userCreate.id &&
            insertedUgr.createdBy == userCreate.id
        }
      }
    }
  }

  test("list users by group: private users are not viewable by those outside of their organization") {
    check {
      forAll {
        (
          platform: Platform,
          mainOrg: Organization.Create,
          altOrg: Organization.Create,
          testingUser: User.Create,
          privateUser: User.Create,
          publicUser: User.Create,
          page: PageRequest
        ) => {
          val usersInPlatformIO = for {
            // Create necessary groups
            dbPlatform <- PlatformDao.create(platform)
            dbMainOrg <- OrganizationDao.create(mainOrg.copy(platformId = dbPlatform.id).toOrganization(true))
            dbAltOrg <- OrganizationDao.create(altOrg.copy(platformId = dbPlatform.id).toOrganization(true))

            // Create necessary users
            dbTestingUser <- UserDao.create(testingUser)
            dbPublicUser <- UserDao.create(publicUser)
            dbPublicUserUpdate <- UserDao.updateUser(dbPublicUser.copy(
              visibility = UserVisibility.Public
            ), dbPublicUser.id)
            dbPrivateUser <- UserDao.create(privateUser)

            // Create user group roles
            ugr1 <- UserGroupRoleDao.create(
              UserGroupRole.Create(
                dbPublicUser.id,
                GroupType.Platform,
                dbPlatform.id,
                GroupRole.Member).toUserGroupRole(dbTestingUser, MembershipStatus.Approved)
            )

            ugr2 <- UserGroupRoleDao.create(
              UserGroupRole.Create(
                dbPrivateUser.id,
                GroupType.Platform,
                dbPlatform.id,
                GroupRole.Member).toUserGroupRole(dbTestingUser, MembershipStatus.Approved)
            )

            ugr3 <- UserGroupRoleDao.create(
              UserGroupRole.Create(
                dbPublicUser.id,
                GroupType.Organization,
                dbMainOrg.id,
                GroupRole.Member).toUserGroupRole(dbTestingUser, MembershipStatus.Approved)
            )

            ugr4 <- UserGroupRoleDao.create(
              UserGroupRole.Create(
                dbPrivateUser.id,
                GroupType.Organization,
                dbAltOrg.id,
                GroupRole.Member).toUserGroupRole(dbTestingUser, MembershipStatus.Approved)
            )

            usersInPlatform <- UserGroupRoleDao.listUsersByGroup(
              GroupType.Platform,
              dbPlatform.id,
              page,
              SearchQueryParameters(Some("")),
              dbPublicUser
            )
          } yield { usersInPlatform }

          val usersInPlatform = usersInPlatformIO.transact(xa).unsafeRunSync

          assert(
            usersInPlatform.count == 1,
            "The private user should not be visible outside of the organization"
          )

          true
        }
      }
    }
  }

  test("list users by group: private users are viewable by those within their organization") {
    check {
      forAll {
        (
          platform: Platform,
          mainOrg: Organization.Create,
          altOrg: Organization.Create,
          testingUser: User.Create,
          privateUser: User.Create,
          publicUser: User.Create,
          page: PageRequest
        ) => {
          val usersInPlatformIO = for {
            // Create necessary groups
            dbPlatform <- PlatformDao.create(platform)
            dbMainOrg <- OrganizationDao.create(mainOrg.copy(platformId = dbPlatform.id).toOrganization(true))

            // Create necessary users
            dbTestingUser <- UserDao.create(testingUser)
            dbPublicUser <- UserDao.create(publicUser)
            dbPublicUserUpdate <- UserDao.updateUser(dbPublicUser.copy(
              visibility = UserVisibility.Public
            ), dbPublicUser.id)
            dbPrivateUser <- UserDao.create(privateUser)

            // Create user group roles
            ugr1 <- UserGroupRoleDao.create(
              UserGroupRole.Create(
                dbPublicUser.id,
                GroupType.Platform,
                dbPlatform.id,
                GroupRole.Member).toUserGroupRole(dbTestingUser, MembershipStatus.Approved)
            )

            ugr2 <- UserGroupRoleDao.create(
              UserGroupRole.Create(
                dbPrivateUser.id,
                GroupType.Platform,
                dbPlatform.id,
                GroupRole.Member).toUserGroupRole(dbTestingUser, MembershipStatus.Approved)
            )

            ugr3 <- UserGroupRoleDao.create(
              UserGroupRole.Create(
                dbPublicUser.id,
                GroupType.Organization,
                dbMainOrg.id,
                GroupRole.Member).toUserGroupRole(dbTestingUser, MembershipStatus.Approved)
            )

            ugr4 <- UserGroupRoleDao.create(
              UserGroupRole.Create(
                dbPrivateUser.id,
                GroupType.Organization,
                dbMainOrg.id,
                GroupRole.Member).toUserGroupRole(dbTestingUser, MembershipStatus.Approved)
            )

            usersInPlatform <- UserGroupRoleDao.listUsersByGroup(
              GroupType.Platform,
              dbPlatform.id,
              page,
              SearchQueryParameters(Some("")),
              dbPublicUser
            )
          } yield { usersInPlatform }

          val usersInPlatform = usersInPlatformIO.transact(xa).unsafeRunSync

          assert(
            usersInPlatform.count == 2,
            "The private user should be visible to those within their organization"
          )

          true
        }
      }
    }
  }

  test("list users by group: public users are viewable by those outside of their organization") {
    check {
      forAll {
        (
          platform: Platform,
          mainOrg: Organization.Create,
          altOrg: Organization.Create,
          testingUser: User.Create,
          privateUser: User.Create,
          publicUser: User.Create,
          page: PageRequest
        ) => {
          val usersInPlatformIO = for {
            // TODO: this could technically be cleaned up by at least starting with the
            // insertUserOrgPlatform and insertUserOrg helpers in PropTestHelpers, but we're
            // a bit under the gun. Future reader from the lands beyond June 2018, if you
            // find this, please consider opening an issue for improving test readability

            // Create necessary groups
            dbPlatform <- PlatformDao.create(platform)
            dbMainOrg <- OrganizationDao.create(mainOrg.copy(platformId = dbPlatform.id).toOrganization(true))
            dbAltOrg <- OrganizationDao.create(altOrg.copy(platformId = dbPlatform.id).toOrganization(true))

            // Create necessary users
            dbTestingUser <- UserDao.create(testingUser)
            dbPublicUser <- UserDao.create(publicUser)
            dbPublicUserUpdate <- UserDao.updateUser(dbPublicUser.copy(
              visibility = UserVisibility.Public
            ), dbPublicUser.id)
            dbPrivateUser <- UserDao.create(privateUser)

            // Create user group roles
            ugr1 <- UserGroupRoleDao.create(
              UserGroupRole.Create(
                dbPublicUser.id,
                GroupType.Platform,
                dbPlatform.id,
                GroupRole.Member).toUserGroupRole(dbTestingUser, MembershipStatus.Approved)
            )

            ugr2 <- UserGroupRoleDao.create(
              UserGroupRole.Create(
                dbPrivateUser.id,
                GroupType.Platform,
                dbPlatform.id,
                GroupRole.Member).toUserGroupRole(dbTestingUser, MembershipStatus.Approved)
            )

            ugr3 <- UserGroupRoleDao.create(
              UserGroupRole.Create(
                dbPublicUser.id,
                GroupType.Organization,
                dbMainOrg.id,
                GroupRole.Member).toUserGroupRole(dbTestingUser, MembershipStatus.Approved)
            )

            ugr4 <- UserGroupRoleDao.create(
              UserGroupRole.Create(
                dbPrivateUser.id,
                GroupType.Organization,
                dbAltOrg.id,
                GroupRole.Member).toUserGroupRole(dbTestingUser, MembershipStatus.Approved)
            )

            usersInPlatform <- UserGroupRoleDao.listUsersByGroup(
              GroupType.Platform,
              dbPlatform.id,
              page,
              SearchQueryParameters(Some("")),
              dbPrivateUser
            )
          } yield { usersInPlatform }

          val usersInPlatform = usersInPlatformIO.transact(xa).unsafeRunSync

          assert(
            usersInPlatform.count == 2,
            "The private user should not be visible outside of the organization"
          )

          true
        }
      }
    }
  }

  test("list users by group: private users are viewable by those on the same team") {
    check {
      forAll {
        (
          platform: Platform,
          mainOrg: Organization.Create,
          altOrg: Organization.Create,
          team: Team.Create,
          testingUser: User.Create,
          privateUser: User.Create,
          publicUser: User.Create,
          page: PageRequest
        ) => {
          val usersInPlatformIO = for {
            dbTestingUser <- UserDao.create(testingUser)

            // Create necessary groups
            dbPlatform <- PlatformDao.create(platform)
            dbMainOrg <- OrganizationDao.create(mainOrg.copy(platformId = dbPlatform.id).toOrganization(true))
            dbAltOrg <- OrganizationDao.create(altOrg.copy(platformId = dbPlatform.id).toOrganization(true))
            dbTeam <- TeamDao.create(team.copy(organizationId = dbMainOrg.id).toTeam(dbTestingUser))

            // Create necessary users
            dbPublicUser <- UserDao.create(publicUser)
            dbPublicUserUpdate <- UserDao.updateUser(dbPublicUser.copy(
              visibility = UserVisibility.Public
            ), dbPublicUser.id)
            dbPrivateUser <- UserDao.create(privateUser)

            // Create user group roles
            ugr1 <- UserGroupRoleDao.create(
              UserGroupRole.Create(
                dbPublicUser.id,
                GroupType.Platform,
                dbPlatform.id,
                GroupRole.Member).toUserGroupRole(dbTestingUser, MembershipStatus.Approved)
            )

            ugr2 <- UserGroupRoleDao.create(
              UserGroupRole.Create(
                dbPrivateUser.id,
                GroupType.Platform,
                dbPlatform.id,
                GroupRole.Member).toUserGroupRole(dbTestingUser, MembershipStatus.Approved)
            )

            ugr3 <- UserGroupRoleDao.create(
              UserGroupRole.Create(
                dbPublicUser.id,
                GroupType.Organization,
                dbMainOrg.id,
                GroupRole.Member).toUserGroupRole(dbTestingUser, MembershipStatus.Approved)
            )

            ugr4 <- UserGroupRoleDao.create(
              UserGroupRole.Create(
                dbPrivateUser.id,
                GroupType.Organization,
                dbAltOrg.id,
                GroupRole.Member).toUserGroupRole(dbTestingUser, MembershipStatus.Approved)
            )

            ugr5 <- UserGroupRoleDao.create(
              UserGroupRole.Create(
                dbPublicUser.id,
                GroupType.Team,
                dbTeam.id,
                GroupRole.Member).toUserGroupRole(dbTestingUser, MembershipStatus.Approved)
            )

            ugr6 <- UserGroupRoleDao.create(
              UserGroupRole.Create(
                dbPrivateUser.id,
                GroupType.Team,
                dbTeam.id,
                GroupRole.Member).toUserGroupRole(dbTestingUser, MembershipStatus.Approved)
            )

            usersInPlatform <- UserGroupRoleDao.listUsersByGroup(
              GroupType.Platform,
              dbPlatform.id,
              page,
              SearchQueryParameters(Some("")),
              dbPublicUser
            )
          } yield { usersInPlatform }

          val usersInPlatform = usersInPlatformIO.transact(xa).unsafeRunSync

          assert(
            usersInPlatform.count == 2,
            "The private user should be visible by those sharing team membership with them"
          )

          true
        }
      }
    }
  }

  test("update a user group role") {
    check {
      forAll {
        (userCreate: User.Create, manager: User.Create, orgCreate: Organization.Create, teamCreate: Team.Create,
         platform: Platform, ugrCreate: UserGroupRole.Create, ugrUpdate: UserGroupRole.Create) => {
          val insertUgrWithRelationsIO = for {
            orgAndUser <- insertUserAndOrg(userCreate, orgCreate, false)
            (insertedOrg, insertedUser) = orgAndUser
            managerUser <- UserDao.create(manager)
            _ <- UserGroupRoleDao.create(
              UserGroupRole.Create(
                managerUser.id, GroupType.Organization, insertedOrg.id, GroupRole.Admin
              ).toUserGroupRole(managerUser, MembershipStatus.Approved)
            )
            insertedTeam <- TeamDao.create(teamCreate.copy(organizationId = insertedOrg.id).toTeam(insertedUser))
            insertedPlatform <- PlatformDao.create(platform)
            insertedUgr <- UserGroupRoleDao.create(
              fixupUserGroupRole(insertedUser, insertedOrg, insertedTeam, insertedPlatform, ugrCreate)
                .toUserGroupRole(managerUser, MembershipStatus.Approved)
            )
          } yield (insertedUgr, insertedUser, managerUser, insertedOrg, insertedTeam, insertedPlatform)

          val updateUgrWithUpdatedAndAffectedRowsIO = insertUgrWithRelationsIO flatMap {
            case (dbUgr: UserGroupRole, dbMember: User, dbAdmin: User, dbOrg: Organization, dbTeam: Team, dbPlatform: Platform) => {
              val fixedUpUgrUpdate = fixupUserGroupRole(dbMember, dbOrg, dbTeam, dbPlatform, ugrUpdate)
                .toUserGroupRole(dbAdmin, MembershipStatus.Approved)
              UserGroupRoleDao.update(fixedUpUgrUpdate, dbUgr.id, dbAdmin) flatMap {
                (affectedRows: Int) => {
                  UserGroupRoleDao.unsafeGetUserGroupRoleById(dbUgr.id, dbMember) map { (affectedRows, _) }
                }
              }
            }
          }

          val (affectedRows, updatedUgr) = updateUgrWithUpdatedAndAffectedRowsIO.transact(xa).unsafeRunSync
          // isActive should always be true since it doesn't exist on UserGroupRole.Creates and is set
          // to true when those are turned into UserGroupRoles
          affectedRows == 1 &&
            updatedUgr.isActive == true &&
            updatedUgr.modifiedBy == manager.id &&
            (updatedUgr.groupType == ugrUpdate.groupType) == (ugrUpdate.groupType == ugrCreate.groupType) &&
            updatedUgr.groupRole == ugrUpdate.groupRole
        }
      }
    }
  }

  test("deactivate a user group role") {
    check {
      forAll {
        (userCreate: User.Create, orgCreate: Organization.Create, teamCreate: Team.Create, platform: Platform, ugrCreate: UserGroupRole.Create) => {
          val insertUgrWithUserIO = for {
            orgAndUser <- insertUserAndOrg(userCreate, orgCreate, false)
            (insertedOrg, insertedUser) = orgAndUser
            insertedTeam <- TeamDao.create(teamCreate.copy(organizationId = insertedOrg.id).toTeam(insertedUser))
            insertedPlatform <- PlatformDao.create(platform)
            insertedUgr <- UserGroupRoleDao.create(
              fixupUserGroupRole(insertedUser, insertedOrg, insertedTeam, insertedPlatform, ugrCreate)
                .toUserGroupRole(insertedUser, MembershipStatus.Approved)
            )
          } yield (insertedUgr, insertedUser)

          val deactivateWithDeactivatedUgrIO = insertUgrWithUserIO flatMap {
            case (dbUgr: UserGroupRole, dbUser: User) => {
              UserGroupRoleDao.deactivate(dbUgr.id, dbUser) flatMap {
                (affectedRows: Int) => {
                  UserGroupRoleDao.unsafeGetUserGroupRoleById(dbUgr.id, dbUser) map { (affectedRows, _) }
                }
              }
            }
          }

          val (affectedRows, updatedUgr) = deactivateWithDeactivatedUgrIO.transact(xa).unsafeRunSync
          affectedRows == 1 &&
            updatedUgr.isActive == false &&
            updatedUgr.modifiedBy == userCreate.id &&
            updatedUgr.groupType == ugrCreate.groupType &&
            updatedUgr.groupRole == ugrCreate.groupRole
        }
      }
    }
  }

  test("deactivate a user's group roles using UserGroupRole.UserGroup") {
    check {
      forAll {
        (userCreate: User.Create, orgCreate: Organization.Create, teamCreate: Team.Create, platform: Platform, ugrCreate: UserGroupRole.Create) => {
          val insertUgrWithUserIO = for {
            orgAndUser <- insertUserAndOrg(userCreate, orgCreate, false)
            (insertedOrg, insertedUser) = orgAndUser
            insertedTeam <- TeamDao.create(teamCreate.copy(organizationId = insertedOrg.id).toTeam(insertedUser))
            insertedPlatform <- PlatformDao.create(platform)
            insertedUgr <- UserGroupRoleDao.create(
              fixupUserGroupRole(insertedUser, insertedOrg, insertedTeam, insertedPlatform, ugrCreate)
                .toUserGroupRole(insertedUser, MembershipStatus.Approved)
            )
          } yield (insertedUgr, insertedUser)

          val deactivateWithDeactivatedUgrIO = insertUgrWithUserIO flatMap {
            case (dbUgr: UserGroupRole, dbUser: User) => {
              UserGroupRoleDao.deactivateUserGroupRoles(
                UserGroupRole.UserGroup(dbUser.id, dbUgr.groupType, dbUgr.groupId),
                dbUser
              )
            }
          }

          val updatedUGRs = deactivateWithDeactivatedUgrIO.transact(xa).unsafeRunSync
          updatedUGRs.size == 1 &&
            updatedUGRs.filter(
              (ugr) => ugr.isActive == false &&
                ugr.modifiedBy == userCreate.id &&
                ugr.groupType == ugrCreate.groupType &&
                ugr.groupRole == ugrCreate.groupRole
            ).size == 1
        }
      }
    }
  }
}
