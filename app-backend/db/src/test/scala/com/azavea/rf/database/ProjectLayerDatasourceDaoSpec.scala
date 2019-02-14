package com.rasterfoundry.database

import com.rasterfoundry.common.datamodel._
import com.rasterfoundry.common.datamodel.Generators.Implicits._

import com.lonelyplanet.akka.http.extensions.PageRequest

import doobie.implicits._
import cats.implicits._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatest.prop.Checkers

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
            page: PageRequest
        ) =>
          {
            val scenesInsertWithUserProjectIO = for {
              orgUserProject <- insertUserOrgProject(user, org, project)
              (_, dbUser, dbProject) = orgUserProject
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
                      dbProject.defaultLayerId
                    ) map { datasources: List[Datasource] =>
                      (dbScenes.map { _.datasource }, datasources)
                    }
                  }
                }
              }
            }

            val (insertedDatasources, listedDatasources) =
              xa.use(t => datasourceListIO.transact(t)).unsafeRunSync
            val insertedIds = insertedDatasources.toSet map {
              (datasource: Datasource.Thin) =>
                datasource.id
            }
            // println(insertedDatasources)
            val listedIds = listedDatasources.toSet map {
              (datasource: Datasource) =>
                datasource.id
            }
            // println(listedIds)
            insertedIds == listedIds
          }
      }
    }
  }
}
