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
         platformCreate: Platform.Create, ugrCreate: UserGroupRole.Create) => {
          val insertUgrIO = for {
            orgAndUser <- insertUserAndOrg(userCreate, orgCreate)
            (insertedOrg, insertedUser) = orgAndUser
            teamAndPlatform <- (
              TeamDao.create(teamCreate.copy(organizationId = insertedOrg.id).toTeam(insertedUser)),
              PlatformDao.create(platformCreate.toPlatform(insertedUser))
            ).tupled
            (insertedTeam, insertedPlatform) = teamAndPlatform
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

  test("update a user group role") {
    check {
      forAll {
        (userCreate: User.Create, manager: User.Create, orgCreate: Organization.Create, teamCreate: Team.Create,
         platformCreate: Platform.Create, ugrCreate: UserGroupRole.Create, ugrUpdate: UserGroupRole.Create) => {
          val insertUgrWithRelationsIO = for {
            orgAndUser <- insertUserAndOrg(userCreate, orgCreate)
            (insertedOrg, insertedUser) = orgAndUser
            managerUser <- UserDao.create(manager.copy(organizationId = insertedOrg.id))
            teamAndPlatform <- (
              TeamDao.create(teamCreate.copy(organizationId = insertedOrg.id).toTeam(managerUser)),
              PlatformDao.create(platformCreate.toPlatform(managerUser))
            ).tupled
            (insertedTeam, insertedPlatform) = teamAndPlatform
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
        (userCreate: User.Create, orgCreate: Organization.Create, teamCreate: Team.Create, platformCreate: Platform.Create, ugrCreate: UserGroupRole.Create) => {
          val insertUgrWithUserIO = for {
            orgAndUser <- insertUserAndOrg(userCreate, orgCreate)
            (insertedOrg, insertedUser) = orgAndUser
            teamAndPlatform <- (
              TeamDao.create(teamCreate.copy(organizationId = insertedOrg.id).toTeam(insertedUser)),
              PlatformDao.create(platformCreate.toPlatform(insertedUser))
            ).tupled
            (insertedTeam, insertedPlatform) = teamAndPlatform
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
}
