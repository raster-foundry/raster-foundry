package com.rasterfoundry.database

import com.rasterfoundry.common.datamodel._
import com.rasterfoundry.common.datamodel.Generators.Implicits._

import com.lonelyplanet.akka.http.extensions.PageRequest

import doobie.implicits._
import cats.implicits._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatest.prop.Checkers

class ProjectScenesDaoSpec
    extends FunSuite
    with Matchers
    with Checkers
    with DBTestConfig
    with PropTestHelpers {
  test("list scenes for a project") {
    check {
      forAll {
        (user: User.Create,
         org: Organization.Create,
         project: Project.Create,
         scenes: List[Scene.Create],
         dsCreate: Datasource.Create,
         page: PageRequest,
         csq: CombinedSceneQueryParams) =>
          {
            val scenesInsertWithUserProjectIO = for {
              (_, dbUser, dbProject) <- insertUserOrgProject(user, org, project)
              datasource <- DatasourceDao.create(dsCreate.toDatasource(dbUser),
                                                 dbUser)
              scenesInsert <- (scenes map {
                fixupSceneCreate(dbUser, datasource, _)
              }).traverse(
                (scene: Scene.Create) => SceneDao.insert(scene, dbUser)
              )
            } yield (scenesInsert, dbUser, dbProject)

            val scenesListIO = scenesInsertWithUserProjectIO flatMap {
              case (dbScenes: List[Scene.WithRelated],
                    dbUser: User,
                    dbProject: Project) => {
                ProjectDao.addScenesToProject(dbScenes map { _.id },
                                              dbProject.id) flatMap { _ =>
                  {
                    ProjectScenesDao.listProjectScenes(dbProject.id, page, csq) map {
                      (paginatedResponse: PaginatedResponse[
                        Scene.ProjectScene]) =>
                        (dbScenes, paginatedResponse.results)
                    }
                  }
                }
              }
            }

            val (insertedScenes, listedScenes) =
              xa.use(t => scenesListIO.transact(t)).unsafeRunSync
            val insertedIds = insertedScenes.toSet map {
              (scene: Scene.WithRelated) =>
                scene.id
            }
            val listedIds = listedScenes.toSet map {
              (scene: Scene.ProjectScene) =>
                scene.id
            }
            (insertedIds & listedIds) == listedIds
          }
      }
    }
  }
}
