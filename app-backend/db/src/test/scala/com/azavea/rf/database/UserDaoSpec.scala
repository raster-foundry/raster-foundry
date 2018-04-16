package com.azavea.rf.database

import com.azavea.rf.datamodel.{Organization, User, UserRole, UserRoleRole, Viewer, Admin, Credential}
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


class UserDaoSpec extends FunSuite with Matchers with Checkers with DBTestConfig {

  // create
  test("inserting users") {
    check(
      forAll(
        (user: User.Create) => {
          val insertedUserIO = for {
            org <- rootOrgQ
            userWithOrg = user.copy(organizationId=org.id)
            created <- UserDao.create(userWithOrg)
          } yield (created)
          val insertedUser = insertedUserIO.transact(xa).unsafeRunSync
          insertedUser.id == user.id
        }
      )
    )
  }

  // createUserWithAuthId
  test("inserting a user with only an auth id") {
    check(
      forAll(
        (authId: String) => {
          val withoutNull = authId.replace('\u0000', 'b')
          UserDao.createUserWithAuthId(withoutNull).transact(xa).unsafeRunSync.id == withoutNull
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
            userWithOrg = userCreate.copy(organizationId=org.id)
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
            userWithOrg = user.copy(organizationId=org.id)
            created <- UserDao.create(userWithOrg)
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
            userWithOrg = user.copy(organizationId=org.id)
            created <- UserDao.create(userWithOrg)
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
            userWithOrg = user.copy(organizationId=org.id)
            created <- UserDao.create(userWithOrg)
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

