package com.rasterfoundry.database

import com.rasterfoundry.common.datamodel._
import com.rasterfoundry.common.datamodel.Generators.Implicits._

import doobie.implicits._
import org.scalatest._
import org.scalacheck.Prop.forAll
import org.scalatest.prop.Checkers

import java.util.UUID

class MapTokenDaoSpec
    extends FunSuite
    with Matchers
    with Checkers
    with DBTestConfig
    with PropTestHelpers {

  test("types") {
    xa.use(t => MapTokenDao.query.list.transact(t))
      .unsafeRunSync
      .length should be >= 0
  }

  test("Retrieve a map token for a project") {
    check {
      forAll {
        (mapTokenCreate: MapToken.Create,
         user: User.Create,
         project: Project.Create) =>
          {
            val retrievedMapTokensIO =
              for {
                dbUser <- UserDao.create(user)
                dbProject <- ProjectDao.insertProject(project, dbUser)
                dbMapToken <- MapTokenDao.insert(
                  fixupMapToken(mapTokenCreate, dbUser, Some(dbProject), None),
                  dbUser
                )
                retrievedGood <- MapTokenDao.checkProject(dbProject.id)(
                  dbMapToken.id)
                retrievedBad <- MapTokenDao.checkProject(UUID.randomUUID)(
                  dbMapToken.id)
              } yield { (dbMapToken, retrievedGood, retrievedBad) }

            val (mapToken, shouldBeSome, shouldBeNone) = xa.use { t =>
              retrievedMapTokensIO.transact(t)
            } unsafeRunSync

            assert(
              Some(mapToken) == shouldBeSome,
              "; Inserted and retrieved map tokens should be the same"
            )
            assert(
              shouldBeNone == None,
              "; Bogus IDs should not return a map token"
            )

            true
          }
      }
    }
  }

  test("Retrieve a map token for an analysis") {
    check {
      forAll {
        (mapTokenCreate: MapToken.Create,
         user: User.Create,
         analysis: ToolRun.Create) =>
          {
            val retrievedMapTokensIO =
              for {
                dbUser <- UserDao.create(user)
                dbAnalysis <- ToolRunDao.insertToolRun(analysis, dbUser)
                dbMapToken <- MapTokenDao.insert(
                  fixupMapToken(mapTokenCreate, dbUser, None, Some(dbAnalysis)),
                  dbUser
                )
                retrievedGood <- MapTokenDao.checkAnalysis(dbAnalysis.id)(
                  dbMapToken.id)
                retrievedBad <- MapTokenDao.checkAnalysis(UUID.randomUUID)(
                  dbMapToken.id)
              } yield { (dbMapToken, retrievedGood, retrievedBad) }

            val (mapToken, shouldBeSome, shouldBeNone) = xa.use { t =>
              retrievedMapTokensIO.transact(t)
            } unsafeRunSync

            assert(
              Some(mapToken) == shouldBeSome,
              "; Inserted and retrieved map tokens should be the same"
            )
            assert(
              shouldBeNone == None,
              "; Bogus IDs should not return a map token"
            )

            true
          }
      }
    }
  }
}
