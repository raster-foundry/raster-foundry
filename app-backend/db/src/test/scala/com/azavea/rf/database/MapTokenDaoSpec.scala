package com.rasterfoundry.database

import com.rasterfoundry.common.Generators.Implicits._
import com.rasterfoundry.datamodel._

import cats.data.{NonEmptyList => NEL}
import cats.implicits._
import doobie.implicits._
import org.scalacheck.Prop.forAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.Checkers

import java.util.UUID

class MapTokenDaoSpec
    extends AnyFunSuite
    with Matchers
    with Checkers
    with DBTestConfig
    with PropTestHelpers {

  test("types") {
    MapTokenDao.query.list.transact(xa).unsafeRunSync.length should be >= 0
  }

  test("Retrieve a map token for a project") {
    check {
      forAll {
        (tokensAndProjects: NEL[(MapToken.Create, Project.Create)],
         tokensAndToolRuns: NEL[(MapToken.Create, ToolRun.Create)],
         user: User.Create) =>
          {
            val retrievedMapTokensIO =
              for {
                dbUser <- UserDao.create(user)
                dbMapToken <- tokensAndProjects traverse {
                  case (token, project) =>
                    ProjectDao.insertProject(project, dbUser) flatMap {
                      dbProject =>
                        MapTokenDao.insert(
                          fixupMapToken(token, dbUser, Some(dbProject), None),
                          dbUser
                        )
                    }
                } map { _.head }
                _ <- tokensAndToolRuns traverse {
                  case (token, toolRun) =>
                    ToolRunDao.insertToolRun(toolRun, dbUser) flatMap {
                      dbToolRun =>
                        MapTokenDao.insert(fixupMapToken(token,
                                                         dbUser,
                                                         None,
                                                         Some(dbToolRun)),
                                           dbUser)
                    }
                }
                retrievedGood <- MapTokenDao.checkProject(
                  dbMapToken.project.get)(dbMapToken.id)
                retrievedBad <- MapTokenDao.checkProject(UUID.randomUUID)(
                  dbMapToken.id)
                listed <- MapTokenDao.listAuthorizedMapTokens(
                  dbUser,
                  CombinedMapTokenQueryParameters(
                    mapTokenParams =
                      MapTokenQueryParameters(projectId = dbMapToken.project)),
                  PageRequest(0, 10, Map.empty))
              } yield { (dbMapToken, retrievedGood, retrievedBad, listed) }

            val (mapToken, shouldBeSome, shouldBeNone, listed) =
              retrievedMapTokensIO.transact(xa).unsafeRunSync

            assert(
              Some(mapToken) == shouldBeSome,
              "; Inserted and retrieved map tokens should be the same"
            )
            assert(
              shouldBeNone == None,
              "; Bogus IDs should not return a map token"
            )
            assert(
              listed.results.length == 1,
              "; Only one map token should have come back for a project"
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

            val (mapToken, shouldBeSome, shouldBeNone) =
              retrievedMapTokensIO.transact(xa).unsafeRunSync

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
