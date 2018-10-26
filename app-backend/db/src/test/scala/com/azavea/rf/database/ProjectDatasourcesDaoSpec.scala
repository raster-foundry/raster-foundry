package com.rasterfoundry.database

import com.rasterfoundry.datamodel._
import com.rasterfoundry.datamodel.Generators.Implicits._
import com.rasterfoundry.database.Implicits._

import com.lonelyplanet.akka.http.extensions.{PageRequest, Order}

import doobie._, doobie.implicits._
import cats._, cats.data._, cats.effect.IO
import cats.implicits._
import doobie.postgres._, doobie.postgres.implicits._
import doobie.scalatest.imports._
import org.scalacheck.Prop.{forAll, exists}
import org.scalatest._
import org.scalatest.prop.Checkers

import java.sql.Timestamp
import java.time.LocalDate

class ProjectDatasourcesDaoSpec
    extends FunSuite
    with Matchers
    with Checkers
    with DBTestConfig
    with PropTestHelpers {
  test("list datasources for a project") {
    check {
      forAll {
        (userCreate: User.Create,
         orgCreate: Organization.Create,
         project: Project.Create,
         dsCreate1: Datasource.Create,
         dsCreate2: Datasource.Create,
         dsCreate3: Datasource.Create,
         scenes1: List[Scene.Create],
         scenes2: List[Scene.Create]) =>
          {

            val expected1 = scenes1 match {
              case Nil => 0
              case _   => 1
            }
            val expected2 = scenes2 match {
              case Nil => 0
              case _   => 1
            }
            val expected = expected1 + expected2

            val createDsIO = for {
              orgUserProjectInsert <- insertUserOrgProject(userCreate,
                                                           orgCreate,
                                                           project)
              (dbOrg, dbUser, dbProject) = orgUserProjectInsert
              dsInsert1 <- fixupDatasource(dsCreate1, dbUser)
              dsInsert2 <- fixupDatasource(dsCreate2, dbUser)
              dsInsert3 <- fixupDatasource(dsCreate3, dbUser)
              scenesInsert1 <- scenes1 traverse { scene =>
                SceneDao.insert(fixupSceneCreate(dbUser, dsInsert1, scene),
                                dbUser)
              }
              scenesInsert2 <- scenes2 traverse { scene =>
                SceneDao.insert(fixupSceneCreate(dbUser, dsInsert2, scene),
                                dbUser)
              }
              _ <- ProjectDao.addScenesToProject(scenesInsert1 map { _.id },
                                                 dbProject.id)
              _ <- ProjectDao.addScenesToProject(scenesInsert2 map { _.id },
                                                 dbProject.id)
              projectDatasources <- ProjectDatasourcesDao
                .listProjectDatasources(dbProject.id)
            } yield (projectDatasources)
            val (pd) = createDsIO.transact(xa).unsafeRunSync
            assert(pd.size == expected,
                   "; Datasources are not duplicated in request")
            true
          }
      }
    }
  }
}
