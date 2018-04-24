package com.azavea.rf.database

import com.azavea.rf.datamodel.{Organization, Platform, User}
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
        (userCreate: User.Create, orgCreate: Organization.Create, platformCreate: Platform.Create) => {
          val insertPlatformIO = for {
            orgAndUser <- insertUserAndOrg(userCreate, orgCreate)
            (_, dbUser) = orgAndUser
            insertedPlatform <- PlatformDao.create(platformCreate.toPlatform(dbUser))
          } yield { insertedPlatform }

          val dbPlatform = insertPlatformIO.transact(xa).unsafeRunSync

          dbPlatform.createdBy == userCreate.id &&
            dbPlatform.modifiedBy == userCreate.id &&
            dbPlatform.name == platformCreate.name &&
            dbPlatform.settings == platformCreate.settings
        }
      }
    }
  }

  test("update a platform") {
    check {
      forAll {
        (userCreate: User.Create, orgCreate: Organization.Create, platformCreate: Platform.Create, platformUpdate: Platform.Create) => {
          val insertPlatformWithUserIO = for {
            orgAndUser <- insertUserAndOrg(userCreate, orgCreate)
            (_, dbUser) = orgAndUser
            insertedPlatform <- PlatformDao.create(platformCreate.toPlatform(dbUser))
          } yield { (insertedPlatform, dbUser) }

          val updatePlatformWithPlatformAndAffectedRowsIO = insertPlatformWithUserIO flatMap {
            case (dbPlatform: Platform, dbUser: User) => {
              PlatformDao.update(platformUpdate.toPlatform(dbUser), dbPlatform.id, dbUser) flatMap {
                (affectedRows: Int) => {
                  PlatformDao.unsafeGetPlatformById(dbPlatform.id) map { (affectedRows, _) }
                }
              }
            }
          }

          val (affectedRows, updatedPlatform) = updatePlatformWithPlatformAndAffectedRowsIO.transact(xa).unsafeRunSync
          affectedRows == 1 &&
            updatedPlatform.name == platformUpdate.name &&
            updatedPlatform.modifiedBy == userCreate.id &&
            updatedPlatform.settings == platformUpdate.settings
        }
      }
    }
  }

  test("delete a platform") {
    check {
      forAll {
        (userCreate: User.Create, orgCreate: Organization.Create, platformCreate: Platform.Create) => {
          val deletePlatformWithPlatformIO = for {
            orgAndUser <- insertUserAndOrg(userCreate, orgCreate)
            (_, dbUser) = orgAndUser
            insertedPlatform <- PlatformDao.create(platformCreate.toPlatform(dbUser))
            deletePlatform <- PlatformDao.delete(insertedPlatform.id)
            byIdPlatform <- PlatformDao.getPlatformById(insertedPlatform.id)
          } yield { (deletePlatform, byIdPlatform) }

          val (rowsAffected, platformById) = deletePlatformWithPlatformIO.transact(xa).unsafeRunSync
          rowsAffected == 1 && platformById == None
        }
      }
    }
  }
}
