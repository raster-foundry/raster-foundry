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
                .toUserGroupRole(insertedUser)
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

  test("list users by group") {
    check {
      forAll {
        (userCreate: User.Create, manager: User.Create, orgCreate: Organization.Create, teamCreate: Team.Create,
         platform: Platform, ugrCreate: UserGroupRole.Create, ugrUpdate: UserGroupRole.Create, page: PageRequest) => {
          val insertUgrWithRelationsIO = for {
            orgAndUser <- insertUserAndOrg(userCreate, orgCreate, false)
            (insertedOrg, insertedUser) = orgAndUser
            managerUser <- UserDao.create(manager)
            _ <- UserGroupRoleDao.create(
              UserGroupRole.Create(
                managerUser.id, GroupType.Organization, insertedOrg.id, GroupRole.Admin
              ).toUserGroupRole(managerUser)
            )
            insertedTeam <- TeamDao.create(teamCreate.copy(organizationId = insertedOrg.id).toTeam(insertedUser))
            insertedPlatform <- PlatformDao.create(platform)
            insertedUgr <- UserGroupRoleDao.create(
              fixupUserGroupRole(insertedUser, insertedOrg, insertedTeam, insertedPlatform, ugrCreate)
                .toUserGroupRole(managerUser)
            )
            usersInGroup <- UserGroupRoleDao.listUsersByGroup(
              insertedUgr.groupType, insertedUgr.groupId, page, SearchQueryParameters(), managerUser
            )
          } yield (usersInGroup, managerUser.id, insertedUser.id, insertedUgr.groupType)

          val (usersInGroup, managerId, userId, groupType) =
            insertUgrWithRelationsIO.transact(xa).unsafeRunSync

          // If the group type is organization, we should get the manager and the user back, otherwise,
          // just the user
          val expectedIds = if (groupType == GroupType.Organization) Set(managerId, userId) else Set(userId)

          // If the group type is organization, we should get the manager and the user back, otherwise,
          // just the user
          val expectedCount = if (groupType == GroupType.Organization) 2 else 1

          assert(usersInGroup.count == expectedCount,
                 "there should be one user in the group unless the group type was organization, then two")
          assert((usersInGroup.results map { _.id } toSet) == expectedIds,
                 "the ids of the users in the group should be the expected ids")
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
              ).toUserGroupRole(managerUser)
            )
            insertedTeam <- TeamDao.create(teamCreate.copy(organizationId = insertedOrg.id).toTeam(insertedUser))
            insertedPlatform <- PlatformDao.create(platform)
            insertedUgr <- UserGroupRoleDao.create(
              fixupUserGroupRole(insertedUser, insertedOrg, insertedTeam, insertedPlatform, ugrCreate)
                .toUserGroupRole(managerUser)
            )
          } yield (insertedUgr, insertedUser, managerUser, insertedOrg, insertedTeam, insertedPlatform)

          val updateUgrWithUpdatedAndAffectedRowsIO = insertUgrWithRelationsIO flatMap {
            case (dbUgr: UserGroupRole, dbMember: User, dbAdmin: User, dbOrg: Organization, dbTeam: Team, dbPlatform: Platform) => {
              val fixedUpUgrUpdate = fixupUserGroupRole(dbMember, dbOrg, dbTeam, dbPlatform, ugrUpdate)
                .toUserGroupRole(dbAdmin)
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
                .toUserGroupRole(insertedUser)
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
                .toUserGroupRole(insertedUser)
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
