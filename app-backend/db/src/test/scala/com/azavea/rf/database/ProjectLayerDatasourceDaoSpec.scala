package com.rasterfoundry.database

import com.rasterfoundry.datamodel._
import com.rasterfoundry.datamodel.Generators.Implicits._
import com.rasterfoundry.database.Implicits._

import com.lonelyplanet.akka.http.extensions.{PageRequest, Order}

import doobie._, doobie.implicits._
import cats._, cats.data._, cats.effect.IO
import cats.implicits._
import doobie.postgres._, doobie.postgres.implicits._
import org.scalacheck.Prop.{forAll, exists}
import org.scalatest._
import org.scalatest.prop.Checkers

import java.sql.Timestamp
import java.time.LocalDate

class ProjectLayerDatasourceDaoSpec
    extends FunSuite
    with Matchers
    with Checkers
    with DBTestConfig
    with PropTestHelpers {
  test("list datasources for a project layer") {
    check {
      forAll {
        (
            user: User.Create,
            org: Organization.Create,
            project: Project.Create,
            scenes: List[Scene.Create],
            dsCreate: Datasource.Create,
            page: PageRequest,
            csq: CombinedSceneQueryParams
        ) =>
          {
            val scenesInsertWithUserProjectIO = for {
              orgUserProject <- insertUserOrgProject(user, org, project)
              (dbOrg, dbUser, dbProject) = orgUserProject
              datasource <- DatasourceDao.create(
                dsCreate.toDatasource(dbUser),
                dbUser
              )
              scenesInsert <- (scenes map {
                fixupSceneCreate(dbUser, datasource, _)
              }).traverse(
                (scene: Scene.Create) => SceneDao.insert(scene, dbUser)
              )
            } yield (scenesInsert, dbUser, dbProject, datasource)

            val datasourceListIO = scenesInsertWithUserProjectIO flatMap {
              case (
                  dbScenes: List[Scene.WithRelated],
                  dbUser: User,
                  dbProject: Project,
                  datasource: Datasource
                  ) => {
                ProjectDao.addScenesToProject(
                  dbScenes map { _.id },
                  dbProject.id
                ) flatMap { _ =>
                  {
                    ProjectLayerDatasourcesDao.listProjectLayerDatasources(
                      dbProject.id,
                      dbProject.defaultLayerId
                    ) map { datasources: List[Datasource] =>
                      (List(datasource), datasources)
                    }
                  }
                }
              }
            }

            val (insertedDatasources, listedDatasources) =
              xa.use(t => datasourceListIO.transact(t)).unsafeRunSync
            val insertedIds = insertedDatasources.toSet map {
              (datasource: Datasource) =>
                datasource.id
            }
            val listedIds = listedDatasources.toSet map {
              (datasource: Datasource) =>
                datasource.id
            }
            insertedIds == listedIds
          }
      }
    }
  }
}
