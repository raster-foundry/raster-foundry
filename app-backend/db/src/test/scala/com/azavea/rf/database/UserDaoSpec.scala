package com.azavea.rf.database

import com.azavea.rf.datamodel._
import com.azavea.rf.datamodel.Generators.Implicits._
import com.azavea.rf.database.Implicits._

import doobie._, doobie.implicits._
import cats._, cats.data._, cats.effect.IO
import cats.syntax.either._
import doobie.postgres._, doobie.postgres.implicits._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatest.prop.Checkers

import scala.util.Random

import com.typesafe.scalalogging.LazyLogging

class UserDaoSpec extends FunSuite
    with Matchers
    with Checkers
    with DBTestConfig
    with LazyLogging
    with PropTestHelpers {

  // create
  test("inserting users") {
    check(
      forAll(
        (user: User.Create) => {
          val insertedUserIO = for {
            org <- rootOrgQ
            created <- UserDao.create(user)
          } yield (created)
          val insertedUser = insertedUserIO.transact(xa).unsafeRunSync
          insertedUser.id == user.id
        }
      )
    )
  }

  // createUserWithJWT
  test("inserting a user using a User.JWTFields") {
    check(
      forAll(
        (
          creatingUser: User.Create, jwtFields: User.JwtFields,
          orgCreate: Organization.Create, platform: Platform
        ) => {
          val insertedUserIO = for {
            creatingUser <- UserDao.create(creatingUser)
            insertedPlatform <- PlatformDao.create(platform)
            insertedOrg <- OrganizationDao.create(orgCreate.copy(platformId = insertedPlatform.id).toOrganization(true))
            newUserAndRoles <- {
              val newUserFields = jwtFields.copy(platformId = insertedPlatform.id, organizationId = insertedOrg.id)
              UserDao.createUserWithJWT(creatingUser, newUserFields)
            }
            (newUser, roles) = newUserAndRoles
            userRoles <- UserGroupRoleDao.listByUser(newUser)
          } yield (newUser, userRoles)

          val (insertedUser, insertedUserRoles) = insertedUserIO.transact(xa).unsafeRunSync
          assert(insertedUser.id == jwtFields.id, "Inserted user should have the same ID as the jwt fields")
          assert(insertedUserRoles.length == 2, "Inserted user should have a role for the org and platform")
          true
        }
      )
    )
  }

  // getUserById
  test("get a user by id") {
    check(
      forAll(
        (userCreate: User.Create) => {
          val createdIdIO = for {
            org <- rootOrgQ
            userWithOrg = userCreate.copy()
            inserted <- UserDao.create(userWithOrg)
            byId <- UserDao.getUserById(inserted.id)
          } yield (byId)
          userCreate.id == (createdIdIO.transact(xa).unsafeRunSync.get.id)
        }
      )
    )
  }

  // updateUser
  test("update users by id") {
    check(
      forAll(
        (user: User.Create, emailNotifications: Boolean, dropboxCredential: Credential, planetCredential: Credential) => {
          val insertedUserIO = for {
            org <- rootOrgQ
            created <- UserDao.create(user)
          } yield (created)
          val (affectedRows, updatedEmailNotifications, updatedDropboxToken, updatedPlanetToken) = (insertedUserIO flatMap {
            case (insertUser: User) => {
              UserDao.updateUser(
                insertUser.copy(planetCredential=planetCredential, dropboxCredential=dropboxCredential, emailNotifications=emailNotifications),
                insertUser.id
              ) flatMap {
                case (affectedRows: Int) => {
                  val updatedPlanetTokenIO = UserDao.unsafeGetUserById(insertUser.id) map {
                    (usr: User) =>
                      ( usr.emailNotifications, usr.dropboxCredential, usr.planetCredential)
                  }
                  updatedPlanetTokenIO map {
                    (t: (Boolean, Credential, Credential)) => (affectedRows, t._1, t._2, t._3)
                  }
                }
              }
            }
          }).transact(xa).unsafeRunSync
          (affectedRows == 1) &&
            (updatedEmailNotifications == emailNotifications) &&
            (updatedDropboxToken == dropboxCredential) &&
            (updatedPlanetToken == planetCredential)
        }
      )
    )
  }

  // storePlanetAccessToken
  test("Updating your own user fields should only update certain ones") {
    check(
      forAll(
        (user: User.Create, planetCredential: Credential, isEmail: Boolean,
          visibility: UserVisibility, email: String
        ) => {
          val insertedUserIO = for {
            org <- rootOrgQ
            created <- UserDao.create(user)
          } yield (created)
          val (affectedRows, updatedUser) = (insertedUserIO flatMap {
            case (created: User) => {
              val updatedUser = created.copy(
                email = email,
                planetCredential = planetCredential,
                emailNotifications = isEmail,
                visibility = visibility
              )
              UserDao.updateOwnUser(updatedUser) flatMap {
                case (affectedRows: Int) => {
                  val updatedUserIO = UserDao.unsafeGetUserById(updatedUser.id)
                  updatedUserIO map { (affectedRows, _) }
                }
              }
            }
          }).transact(xa).unsafeRunSync

          affectedRows == 1 &&
            updatedUser.emailNotifications == isEmail &&
            updatedUser.visibility == visibility &&
            updatedUser.planetCredential.token == planetCredential.token &&
            updatedUser.email != email &&
            updatedUser.email == user.email
        }
      )
    )
  }

  // storeDropboxAccessToken
  test("set a dropbox token") {
    check(
      forAll(
        (user: User.Create, dropboxCredential: Credential) => {
          val insertedUserIO = for {
            org <- rootOrgQ
            created <- UserDao.create(user)
          } yield (created)
          val (affectedRows, updatedToken) = (insertedUserIO flatMap {
            case (insertUser: User) => {
              UserDao.storeDropboxAccessToken(insertUser.id, dropboxCredential) flatMap {
                case (affectedRows: Int) => {
                  val updatedDbxTokenIO = UserDao.unsafeGetUserById(insertUser.id) map { _.dropboxCredential }
                  updatedDbxTokenIO map { (affectedRows, _) }
                }
              }
            }
          }).transact(xa).unsafeRunSync
          (updatedToken.token == dropboxCredential.token) && (affectedRows == 1)
        }
      )
    )
  }

  test("list visibile users") {
    check {
      forAll(
        (uc1: User.Create, uc2: User.Create,
         uc3: User.Create, uc4: User.Create,
         pc1: Platform, pc2: Platform,
         org1: Organization.Create, org2: Organization.Create) => {
          val defaultUser = uc1.toUser.copy(id = "default")
          val orgsIO = for {
            p1 <- PlatformDao.create(pc1)
            p2 <- PlatformDao.create(pc2)
            org1 <- OrganizationDao.createOrganization(org1.copy(platformId = p1.id))
            org2 <- OrganizationDao.createOrganization(org2.copy(platformId = p2.id))
            u1i <- UserDao.create(uc1)
            u1 = u1i.copy(visibility=UserVisibility.Private)
            u2 <- UserDao.create(uc2)
            u3i <- UserDao.create(uc3)
            u3 = u3i.copy(visibility=UserVisibility.Public)
            _ <- UserDao.updateUser(u3, u3i.id)
            u4i <- UserDao.create(uc4)
            u4 = u4i.copy(visibility=UserVisibility.Public)
            _ <- UserDao.updateUser(u4, u4i.id)
            _ <- UserGroupRoleDao.create(
              UserGroupRole.Create(u1.id, GroupType.Platform, p1.id, GroupRole.Member)
                .toUserGroupRole(defaultUser, MembershipStatus.Approved)
            )
            _ <- UserGroupRoleDao.create(
              UserGroupRole.Create(u1.id, GroupType.Organization, org1.id, GroupRole.Member)
                .toUserGroupRole(defaultUser, MembershipStatus.Approved)
            )
            _ <- UserGroupRoleDao.create(
              UserGroupRole.Create(u2.id, GroupType.Platform, p1.id, GroupRole.Member)
                .toUserGroupRole(defaultUser, MembershipStatus.Approved)
            )
            _ <- UserGroupRoleDao.create(
              UserGroupRole.Create(u2.id, GroupType.Organization, org1.id, GroupRole.Member)
                .toUserGroupRole(defaultUser, MembershipStatus.Approved)
            )
            u3platugr <- UserGroupRoleDao.create(
              UserGroupRole.Create(u3.id, GroupType.Platform, p1.id, GroupRole.Member)
                .toUserGroupRole(defaultUser, MembershipStatus.Approved)
            )
            _ <- UserGroupRoleDao.create(
              UserGroupRole.Create(u3.id, GroupType.Platform, p2.id, GroupRole.Member)
                .toUserGroupRole(defaultUser, MembershipStatus.Approved)
            )
            u1VisibleUsers <- UserDao.viewFilter(u1).list
            u2VisibleUsers <- UserDao.viewFilter(u2).list
            u3VisibleUsers <- UserDao.viewFilter(u3).list
            _ <- UserGroupRoleDao.update(u3platugr.copy(groupRole = GroupRole.Admin), u3platugr.id, u2)
            u3AdminVisibleUsers <- UserDao.viewFilter(u3).list
          } yield { (u1, u2, u3, u4, u1VisibleUsers, u2VisibleUsers, u3VisibleUsers, u3AdminVisibleUsers) }
          orgsIO.transact(xa).unsafeRunSync

          val (u1, u2, u3, u4, u1users, u2users, u3users, u3usersAdmin) = orgsIO.transact(xa).unsafeRunSync
          val u1userids = u1users.toSet.map { u: User =>  u.id }
          val u2userids = u2users.toSet.map { u: User => u.id }
          val u3userids = u3users.toSet.map { u: User => u.id }
          val u3useridsAdmin = u3usersAdmin.toSet.map {u: User =>  u.id }
          assert(u2userids.contains(u1.id),
                 "; members of orgs should be able to see each other if they are hidden")
          assert(u1userids.contains(u3.id),
                 "; members should be able to see public users on the same platform")
          assert(!u1userids.contains(u4.id),
                 "; members should not be able to see public users on other platforms")
          assert(!u3userids.contains(u1.id),
                 "; platform members should not see hidden users on the same platform")
          assert(u3useridsAdmin.contains(u1.id),
                 "; platform admins should be able to see hidden users on their platform")
          true
        }
      )
    }
  }

  // getUsersByIds
  test("bulk lookup users by id") {
    check {
      forAll {
        (user1: User.Create, user2: User.Create, user3: User.Create, org: Organization.Create) => {
          val outUsersIO = for {
            orgAndUser <- insertUserAndOrg(user1, org)
            (_, dbUser1) = orgAndUser
            dbUser2 <- UserDao.create(user2)
            _ <- UserDao.create(user3)
            listedUsers <- UserDao.getUsersByIds(List(dbUser1.id, dbUser2.id))
          } yield { listedUsers }

          val outUsers = outUsersIO.transact(xa).unsafeRunSync
          assert(outUsers.map( _.id ).toSet == Set(user1.id, user2.id),
                 "Lookup by ids should return the correct set of users")
          true
        }
      }
    }
  }

  test("Getting another user's info should not return credentials") {
    check {
      forAll {
        (user: User.Create, org: Organization.Create) => {
          val createdUserIO = for {
            orgAndUser <- insertUserAndOrg(user, org)
            (_, dbUser) = orgAndUser
            createdUser <- UserDao.unsafeGetUserById(dbUser.id, Some(false))
          } yield(createdUser)
          val createdUser = createdUserIO.transact(xa).unsafeRunSync
          createdUser.planetCredential == Credential(Some("")) &&
            createdUser.dropboxCredential == Credential(Some(""))
        }
      }
    }
  }

  test("Lookup a user's platform") {
    check {
      forAll {
        (user: User.Create, org: Organization.Create, platform: Platform) => {
          val platformsIO = for {
            userOrgPlat <- insertUserOrgPlatform(user, org, platform, true)
            (dbUser, _, insertedPlatform) = userOrgPlat
            listedPlatform <- UserDao.unsafeGetUserPlatform(dbUser.id)
          } yield (insertedPlatform, listedPlatform)

          val (inserted, listed) = platformsIO.transact(xa).unsafeRunSync
          assert(inserted == listed,
                 "Unsafe get of a user's platform should return the user's platform")
          true
        }
      }
    }
  }
}
