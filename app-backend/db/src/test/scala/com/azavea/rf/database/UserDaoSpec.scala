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

class UserDaoSpec extends FunSuite with Matchers with Checkers with DBTestConfig with LazyLogging {

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
            insertedOrg <- OrganizationDao.create(orgCreate.copy(platformId = insertedPlatform.id).toOrganization)
            newUser <- {
              val newUserFields = jwtFields.copy(platformId = insertedPlatform.id, organizationId = insertedOrg.id)
              UserDao.createUserWithJWT(creatingUser, newUserFields)
            }
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
  test("set a planet credential") {
    check(
      forAll(
        (user: User.Create, planetCredential: Credential) => {
          val insertedUserIO = for {
            org <- rootOrgQ
            created <- UserDao.create(user)
          } yield (created)
          val (affectedRows, updatedToken) = (insertedUserIO flatMap {
            case (insertUser: User) => {
              UserDao.storePlanetAccessToken(insertUser, insertUser.copy(planetCredential=planetCredential)) flatMap {
                case (affectedRows: Int) => {
                  val updatedPlanetTokenIO = UserDao.unsafeGetUserById(insertUser.id) map { _.planetCredential }
                  updatedPlanetTokenIO map { (affectedRows, _) }
                }
              }
            }
          }).transact(xa).unsafeRunSync
          (updatedToken.token == planetCredential.token) && (affectedRows == 1)
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
}

