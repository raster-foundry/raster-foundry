package com.rasterfoundry.database

import com.rasterfoundry.common.datamodel._
import com.rasterfoundry.common.datamodel.Generators.Implicits._

import doobie.implicits._
import cats.implicits._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatest.prop.Checkers

class ProjectDatasourcesDaoSpec
    extends FunSuite
    with Matchers
    with Checkers
    with DBTestConfig
    with PropTestHelpers {
  test("list datasources for a project") {
    check {
      forAll {
        (baseData: (User.Create, Organization.Create, Platform, Project.Create),
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

            val (userCreate, orgCreate, platform, project) = baseData

            val createDsIO = for {
              (dbUser, _, _, dbProject) <- insertUserOrgPlatProject(userCreate,
                                                                    orgCreate,
                                                                    platform,
                                                                    project)
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
            val pd = xa.use(t => createDsIO.transact(t)).unsafeRunSync
            assert(pd.size == expected,
                   "; Datasources are not duplicated in request")
            true
          }
      }
    }
  }
}
