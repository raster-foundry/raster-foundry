package com.rasterfoundry.database

import com.rasterfoundry.common.Generators.Implicits._
import com.rasterfoundry.datamodel.PageRequest
import com.rasterfoundry.datamodel._

import cats.implicits._
import doobie.implicits._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatestplus.scalacheck.Checkers
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class UserGroupRoleDaoSpec
    extends AnyFunSuite
    with Matchers
    with Checkers
    with DBTestConfig
    with PropTestHelpers {

  test("list user group roles") {
    UserGroupRoleDao.query.list.transact(xa).unsafeRunSync.length should be >= 0
  }

  test("insert a user group role") {
    check {
      forAll {
        (
            userCreate: User.Create,
            orgCreate: Organization.Create,
            platform: Platform,
            teamCreate: Team.Create,
            ugrCreate: UserGroupRole.Create
        ) =>
          {
            val insertUgrIO = for {
              (insertedUser, insertedOrg, insertedPlatform) <- insertUserOrgPlatform(
                userCreate,
                orgCreate,
                platform,
                false
              )
              insertedTeam <- TeamDao.create(
                teamCreate
                  .copy(organizationId = insertedOrg.id)
                  .toTeam(insertedUser)
              )
              insertedUgr <- UserGroupRoleDao.create(
                fixupUserGroupRole(
                  insertedUser,
                  insertedOrg,
                  insertedTeam,
                  insertedPlatform,
                  ugrCreate
                ).toUserGroupRole(insertedUser, MembershipStatus.Approved)
              )
            } yield { insertedUgr }

            val insertedUgr = insertUgrIO.transact(xa).unsafeRunSync

            insertedUgr.userId == userCreate.id &&
            insertedUgr.groupType == ugrCreate.groupType &&
            insertedUgr.groupRole == ugrCreate.groupRole &&
            insertedUgr.createdBy == userCreate.id
          }
      }
    }
  }

  test(
    "list users by group: private users are not viewable by those outside of their organization"
  ) {
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
        ) =>
          {
            val usersInPlatformIO = for {
              // Create necessary groups
              dbPlatform <- PlatformDao.create(platform)
              dbMainOrg <- OrganizationDao.create(
                mainOrg.copy(platformId = dbPlatform.id).toOrganization(true)
              )
              dbAltOrg <- OrganizationDao.create(
                altOrg.copy(platformId = dbPlatform.id).toOrganization(true)
              )

              // Create necessary users
              dbTestingUser <- UserDao.create(testingUser)
              dbPublicUser <- UserDao.create(publicUser)
              _ <- UserDao.updateUser(
                dbPublicUser.copy(
                  visibility = UserVisibility.Public
                ),
                dbPublicUser.id
              )
              dbPrivateUser <- UserDao.create(privateUser)

              // Create user group roles
              _ <- UserGroupRoleDao.create(
                UserGroupRole
                  .Create(
                    dbPublicUser.id,
                    GroupType.Platform,
                    dbPlatform.id,
                    GroupRole.Member
                  )
                  .toUserGroupRole(dbTestingUser, MembershipStatus.Approved)
              )

              _ <- UserGroupRoleDao.create(
                UserGroupRole
                  .Create(
                    dbPrivateUser.id,
                    GroupType.Platform,
                    dbPlatform.id,
                    GroupRole.Member
                  )
                  .toUserGroupRole(dbTestingUser, MembershipStatus.Approved)
              )

              _ <- UserGroupRoleDao.create(
                UserGroupRole
                  .Create(
                    dbPublicUser.id,
                    GroupType.Organization,
                    dbMainOrg.id,
                    GroupRole.Member
                  )
                  .toUserGroupRole(dbTestingUser, MembershipStatus.Approved)
              )

              _ <- UserGroupRoleDao.create(
                UserGroupRole
                  .Create(
                    dbPrivateUser.id,
                    GroupType.Organization,
                    dbAltOrg.id,
                    GroupRole.Member
                  )
                  .toUserGroupRole(dbTestingUser, MembershipStatus.Approved)
              )

              usersInPlatform <- UserGroupRoleDao.listUsersByGroup(
                GroupType.Platform,
                dbPlatform.id,
                page,
                SearchQueryParameters(Some("")),
                dbPublicUser
              )
            } yield { usersInPlatform }

            val usersInPlatform =
              usersInPlatformIO.transact(xa).unsafeRunSync

            assert(
              usersInPlatform.count == 1,
              "The private user should not be visible outside of the organization"
            )

            true
          }
      }
    }
  }

  test(
    "list users by group: private users are viewable by those within their organization"
  ) {
    check {
      forAll {
        (
            platform: Platform,
            mainOrg: Organization.Create,
            testingUser: User.Create,
            privateUser: User.Create,
            publicUser: User.Create,
            page: PageRequest
        ) =>
          {
            val usersInPlatformIO = for {
              // Create necessary groups
              dbPlatform <- PlatformDao.create(platform)
              dbMainOrg <- OrganizationDao.create(
                mainOrg.copy(platformId = dbPlatform.id).toOrganization(true)
              )

              // Create necessary users
              dbTestingUser <- UserDao.create(testingUser)
              dbPublicUser <- UserDao.create(publicUser)
              _ <- UserDao.updateUser(
                dbPublicUser.copy(
                  visibility = UserVisibility.Public
                ),
                dbPublicUser.id
              )
              dbPrivateUser <- UserDao.create(privateUser)

              // Create user group roles
              _ <- UserGroupRoleDao.create(
                UserGroupRole
                  .Create(
                    dbPublicUser.id,
                    GroupType.Platform,
                    dbPlatform.id,
                    GroupRole.Member
                  )
                  .toUserGroupRole(dbTestingUser, MembershipStatus.Approved)
              )

              _ <- UserGroupRoleDao.create(
                UserGroupRole
                  .Create(
                    dbPrivateUser.id,
                    GroupType.Platform,
                    dbPlatform.id,
                    GroupRole.Member
                  )
                  .toUserGroupRole(dbTestingUser, MembershipStatus.Approved)
              )

              _ <- UserGroupRoleDao.create(
                UserGroupRole
                  .Create(
                    dbPublicUser.id,
                    GroupType.Organization,
                    dbMainOrg.id,
                    GroupRole.Member
                  )
                  .toUserGroupRole(dbTestingUser, MembershipStatus.Approved)
              )

              _ <- UserGroupRoleDao.create(
                UserGroupRole
                  .Create(
                    dbPrivateUser.id,
                    GroupType.Organization,
                    dbMainOrg.id,
                    GroupRole.Member
                  )
                  .toUserGroupRole(dbTestingUser, MembershipStatus.Approved)
              )

              usersInPlatform <- UserGroupRoleDao.listUsersByGroup(
                GroupType.Platform,
                dbPlatform.id,
                page,
                SearchQueryParameters(Some("")),
                dbPublicUser
              )
            } yield { usersInPlatform }

            val usersInPlatform =
              usersInPlatformIO.transact(xa).unsafeRunSync

            assert(
              usersInPlatform.count == 2,
              "The private user should be visible to those within their organization"
            )

            true
          }
      }
    }
  }

  test(
    "list users by group: public users are viewable by those outside of their organization"
  ) {
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
        ) =>
          {
            val usersInPlatformIO = for {
              // TODO: this could technically be cleaned up by at least starting with the
              // insertUserOrgPlatform and insertUserOrg helpers in PropTestHelpers, but we're
              // a bit under the gun. Future reader from the lands beyond June 2018, if you
              // find this, please consider opening an issue for improving test readability

              // Create necessary groups
              dbPlatform <- PlatformDao.create(platform)
              dbMainOrg <- OrganizationDao.create(
                mainOrg.copy(platformId = dbPlatform.id).toOrganization(true)
              )
              dbAltOrg <- OrganizationDao.create(
                altOrg.copy(platformId = dbPlatform.id).toOrganization(true)
              )

              // Create necessary users
              dbTestingUser <- UserDao.create(testingUser)
              dbPublicUser <- UserDao.create(publicUser)
              _ <- UserDao.updateUser(
                dbPublicUser.copy(
                  visibility = UserVisibility.Public
                ),
                dbPublicUser.id
              )
              dbPrivateUser <- UserDao.create(privateUser)

              // Create user group roles
              _ <- UserGroupRoleDao.create(
                UserGroupRole
                  .Create(
                    dbPublicUser.id,
                    GroupType.Platform,
                    dbPlatform.id,
                    GroupRole.Member
                  )
                  .toUserGroupRole(dbTestingUser, MembershipStatus.Approved)
              )

              _ <- UserGroupRoleDao.create(
                UserGroupRole
                  .Create(
                    dbPrivateUser.id,
                    GroupType.Platform,
                    dbPlatform.id,
                    GroupRole.Member
                  )
                  .toUserGroupRole(dbTestingUser, MembershipStatus.Approved)
              )

              _ <- UserGroupRoleDao.create(
                UserGroupRole
                  .Create(
                    dbPublicUser.id,
                    GroupType.Organization,
                    dbMainOrg.id,
                    GroupRole.Member
                  )
                  .toUserGroupRole(dbTestingUser, MembershipStatus.Approved)
              )

              _ <- UserGroupRoleDao.create(
                UserGroupRole
                  .Create(
                    dbPrivateUser.id,
                    GroupType.Organization,
                    dbAltOrg.id,
                    GroupRole.Member
                  )
                  .toUserGroupRole(dbTestingUser, MembershipStatus.Approved)
              )

              usersInPlatform <- UserGroupRoleDao.listUsersByGroup(
                GroupType.Platform,
                dbPlatform.id,
                page,
                SearchQueryParameters(Some("")),
                dbPrivateUser
              )
            } yield { usersInPlatform }

            val usersInPlatform =
              usersInPlatformIO.transact(xa).unsafeRunSync

            assert(
              usersInPlatform.count == 2,
              "The private user should not be visible outside of the organization"
            )

            true
          }
      }
    }
  }

  test(
    "list users by group: private users are viewable by those on the same team"
  ) {
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
        ) =>
          {
            val usersInPlatformIO = for {
              dbTestingUser <- UserDao.create(testingUser)

              // Create necessary groups
              dbPlatform <- PlatformDao.create(platform)
              dbMainOrg <- OrganizationDao.create(
                mainOrg.copy(platformId = dbPlatform.id).toOrganization(true)
              )
              dbAltOrg <- OrganizationDao.create(
                altOrg.copy(platformId = dbPlatform.id).toOrganization(true)
              )
              dbTeam <- TeamDao.create(
                team.copy(organizationId = dbMainOrg.id).toTeam(dbTestingUser)
              )

              // Create necessary users
              dbPublicUser <- UserDao.create(publicUser)
              _ <- UserDao.updateUser(
                dbPublicUser.copy(
                  visibility = UserVisibility.Public
                ),
                dbPublicUser.id
              )
              dbPrivateUser <- UserDao.create(privateUser)

              // Create user group roles
              _ <- UserGroupRoleDao.create(
                UserGroupRole
                  .Create(
                    dbPublicUser.id,
                    GroupType.Platform,
                    dbPlatform.id,
                    GroupRole.Member
                  )
                  .toUserGroupRole(dbTestingUser, MembershipStatus.Approved)
              )

              _ <- UserGroupRoleDao.create(
                UserGroupRole
                  .Create(
                    dbPrivateUser.id,
                    GroupType.Platform,
                    dbPlatform.id,
                    GroupRole.Member
                  )
                  .toUserGroupRole(dbTestingUser, MembershipStatus.Approved)
              )

              _ <- UserGroupRoleDao.create(
                UserGroupRole
                  .Create(
                    dbPublicUser.id,
                    GroupType.Organization,
                    dbMainOrg.id,
                    GroupRole.Member
                  )
                  .toUserGroupRole(dbTestingUser, MembershipStatus.Approved)
              )

              _ <- UserGroupRoleDao.create(
                UserGroupRole
                  .Create(
                    dbPrivateUser.id,
                    GroupType.Organization,
                    dbAltOrg.id,
                    GroupRole.Member
                  )
                  .toUserGroupRole(dbTestingUser, MembershipStatus.Approved)
              )

              _ <- UserGroupRoleDao.create(
                UserGroupRole
                  .Create(
                    dbPublicUser.id,
                    GroupType.Team,
                    dbTeam.id,
                    GroupRole.Member
                  )
                  .toUserGroupRole(dbTestingUser, MembershipStatus.Approved)
              )

              _ <- UserGroupRoleDao.create(
                UserGroupRole
                  .Create(
                    dbPrivateUser.id,
                    GroupType.Team,
                    dbTeam.id,
                    GroupRole.Member
                  )
                  .toUserGroupRole(dbTestingUser, MembershipStatus.Approved)
              )

              usersInPlatform <- UserGroupRoleDao.listUsersByGroup(
                GroupType.Platform,
                dbPlatform.id,
                page,
                SearchQueryParameters(Some("")),
                dbPublicUser
              )
            } yield { usersInPlatform }

            val usersInPlatform =
              usersInPlatformIO.transact(xa).unsafeRunSync

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
        (
            userCreate: User.Create,
            manager: User.Create,
            orgCreate: Organization.Create,
            platform: Platform,
            teamCreate: Team.Create,
            ugrCreate: UserGroupRole.Create,
            ugrUpdate: UserGroupRole.Create
        ) =>
          {
            val insertUgrWithRelationsIO = for {
              (insertedUser, insertedOrg, insertedPlatform) <- insertUserOrgPlatform(
                userCreate,
                orgCreate,
                platform,
                false
              )
              managerUser <- UserDao.create(manager)
              _ <- UserGroupRoleDao.create(
                UserGroupRole
                  .Create(
                    managerUser.id,
                    GroupType.Organization,
                    insertedOrg.id,
                    GroupRole.Admin
                  )
                  .toUserGroupRole(managerUser, MembershipStatus.Approved)
              )
              insertedTeam <- TeamDao.create(
                teamCreate
                  .copy(organizationId = insertedOrg.id)
                  .toTeam(insertedUser)
              )
              insertedUgr <- UserGroupRoleDao.create(
                fixupUserGroupRole(
                  insertedUser,
                  insertedOrg,
                  insertedTeam,
                  insertedPlatform,
                  ugrCreate
                ).toUserGroupRole(managerUser, MembershipStatus.Approved)
              )
            } yield
              (
                insertedUgr,
                insertedUser,
                managerUser,
                insertedOrg,
                insertedTeam,
                insertedPlatform
              )

            val updateUgrWithUpdatedAndAffectedRowsIO = insertUgrWithRelationsIO flatMap {
              case (
                  dbUgr: UserGroupRole,
                  dbMember: User,
                  dbAdmin: User,
                  dbOrg: Organization,
                  dbTeam: Team,
                  dbPlatform: Platform
                  ) => {
                val fixedUpUgrUpdate = fixupUserGroupRole(
                  dbMember,
                  dbOrg,
                  dbTeam,
                  dbPlatform,
                  ugrUpdate
                ).toUserGroupRole(dbAdmin, MembershipStatus.Approved)
                UserGroupRoleDao.update(fixedUpUgrUpdate, dbUgr.id) flatMap {
                  (affectedRows: Int) =>
                    {
                      UserGroupRoleDao.unsafeGetUserGroupRoleById(
                        dbUgr.id,
                        dbMember
                      ) map { (affectedRows, _) }
                    }
                }
              }
            }

            val (affectedRows, updatedUgr) =
              updateUgrWithUpdatedAndAffectedRowsIO.transact(xa).unsafeRunSync
            // isActive should always be true since it doesn't exist on UserGroupRole.Creates and is set
            // to true when those are turned into UserGroupRoles
            affectedRows == 1 &&
            updatedUgr.isActive == true &&
            (updatedUgr.groupType == ugrUpdate.groupType) == (ugrUpdate.groupType == ugrCreate.groupType) &&
            updatedUgr.groupRole == ugrUpdate.groupRole
          }
      }
    }
  }

  test("deactivate a user group role") {
    check {
      forAll {
        (
            userCreate: User.Create,
            orgCreate: Organization.Create,
            platform: Platform,
            teamCreate: Team.Create,
            ugrCreate: UserGroupRole.Create
        ) =>
          {
            val insertUgrWithUserIO = for {
              (insertedUser, insertedOrg, insertedPlatform) <- insertUserOrgPlatform(
                userCreate,
                orgCreate,
                platform,
                false
              )
              insertedTeam <- TeamDao.create(
                teamCreate
                  .copy(organizationId = insertedOrg.id)
                  .toTeam(insertedUser)
              )
              insertedUgr <- UserGroupRoleDao.create(
                fixupUserGroupRole(
                  insertedUser,
                  insertedOrg,
                  insertedTeam,
                  insertedPlatform,
                  ugrCreate
                ).toUserGroupRole(insertedUser, MembershipStatus.Approved)
              )
            } yield (insertedUgr, insertedUser)

            val deactivateWithDeactivatedUgrIO = insertUgrWithUserIO flatMap {
              case (dbUgr: UserGroupRole, dbUser: User) => {
                UserGroupRoleDao.deactivate(dbUgr.id) flatMap {
                  (affectedRows: Int) =>
                    {
                      UserGroupRoleDao.unsafeGetUserGroupRoleById(
                        dbUgr.id,
                        dbUser
                      ) map {
                        (affectedRows, _)
                      }
                    }
                }
              }
            }

            val (affectedRows, updatedUgr) =
              deactivateWithDeactivatedUgrIO.transact(xa).unsafeRunSync
            affectedRows == 1 &&
            updatedUgr.isActive == false &&
            updatedUgr.groupType == ugrCreate.groupType &&
            updatedUgr.groupRole == ugrCreate.groupRole
          }
      }
    }
  }

  test("deactivate a user's group roles using UserGroupRole.UserGroup") {
    check {
      forAll {
        (
            userCreate: User.Create,
            orgCreate: Organization.Create,
            teamCreate: Team.Create,
            platform: Platform,
            ugrCreate: UserGroupRole.Create
        ) =>
          {
            val insertUgrWithUserIO = for {
              (insertedUser, insertedOrg, insertedPlatform) <- insertUserOrgPlatform(
                userCreate,
                orgCreate,
                platform,
                false
              )
              insertedTeam <- TeamDao.create(
                teamCreate
                  .copy(organizationId = insertedOrg.id)
                  .toTeam(insertedUser)
              )
              insertedUgr <- UserGroupRoleDao.create(
                fixupUserGroupRole(
                  insertedUser,
                  insertedOrg,
                  insertedTeam,
                  insertedPlatform,
                  ugrCreate
                ).toUserGroupRole(insertedUser, MembershipStatus.Approved)
              )
            } yield (insertedUgr, insertedUser)

            val deactivateWithDeactivatedUgrIO = insertUgrWithUserIO flatMap {
              case (dbUgr: UserGroupRole, dbUser: User) => {
                UserGroupRoleDao.deactivateUserGroupRoles(
                  UserGroupRole
                    .UserGroup(dbUser.id, dbUgr.groupType, dbUgr.groupId)
                )
              }
            }

            val updatedUGRs =
              deactivateWithDeactivatedUgrIO.transact(xa).unsafeRunSync
            updatedUGRs.size == 1 &&
            updatedUGRs
              .filter(
                (ugr) =>
                  ugr.isActive == false &&
                    ugr.groupType == ugrCreate.groupType &&
                    ugr.groupRole == ugrCreate.groupRole
              )
              .size == 1
          }
      }
    }
  }

  test("Get user group roles with group names") {
    check {
      forAll {
        (
            userCreate: User.Create,
            platform: Platform,
            orgCreate: Organization.Create,
            teamCreate: Team.Create
        ) =>
          {
            val getUgrWithNameIO = for {
              userOrgPlat <- insertUserOrgPlatform(
                userCreate,
                orgCreate,
                platform,
                true
              )
              (dbUser, dbOrg, dbPlat) = userOrgPlat
              dbTeam <- TeamDao.create(
                teamCreate
                  .copy(organizationId = dbOrg.id)
                  .toTeam(dbUser)
              )
              _ <- UserGroupRoleDao.create(
                UserGroupRole
                  .Create(
                    dbUser.id,
                    GroupType.Organization,
                    dbOrg.id,
                    GroupRole.Member
                  )
                  .toUserGroupRole(dbUser, MembershipStatus.Approved)
              )
              _ <- UserGroupRoleDao.create(
                UserGroupRole
                  .Create(
                    dbUser.id,
                    GroupType.Team,
                    dbTeam.id,
                    GroupRole.Member
                  )
                  .toUserGroupRole(dbUser, MembershipStatus.Approved)
              )
              ugrWithName <- UserGroupRoleDao.listByUserWithRelated(dbUser)
            } yield { (ugrWithName, dbPlat, dbOrg, dbTeam) }

            val (ugrWithName, dbPlat, dbOrg, dbTeam) =
              getUgrWithNameIO.transact(xa).unsafeRunSync
            val groupNames = ugrWithName.map(_.groupName)

            val realGroupNames = List(dbPlat.name, dbOrg.name, dbTeam.name)

            assert(
              realGroupNames.diff(groupNames).length == 0,
              "Inserted UGR group names should match inserted plat, org, and team names"
            )
            true
          }
      }
    }
  }
}
