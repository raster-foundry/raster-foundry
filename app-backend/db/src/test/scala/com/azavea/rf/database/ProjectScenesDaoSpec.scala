package com.azavea.rf.database

import com.azavea.rf.datamodel._
import com.azavea.rf.datamodel.Generators.Implicits._
import com.azavea.rf.database.Implicits._

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

class ProjectScenesDaoSpec extends FunSuite with Matchers with Checkers with DBTestConfig with PropTestHelpers {
  test("list scenes for a project") {
    check {
      forAll {
        (user: User.Create, org: Organization.Create, project: Project.Create, scenes: List[Scene.Create],
         dsCreate: Datasource.Create, page: PageRequest, csq: CombinedSceneQueryParams) => {
          val scenesInsertWithUserProjectIO = for {
            orgUserProject <- insertUserOrgProject(user, org, project)
            (dbOrg, dbUser, dbProject) = orgUserProject
            datasource <- DatasourceDao.create(dsCreate.toDatasource(dbUser), dbUser)
            scenesInsert <- (scenes map { fixupSceneCreate(dbUser, datasource, _) }).traverse(
              (scene: Scene.Create) => SceneDao.insert(scene, dbUser)
            )
          } yield (scenesInsert, dbUser, dbProject)

          val scenesListIO = scenesInsertWithUserProjectIO flatMap {
            case (dbScenes: List[Scene.WithRelated], dbUser: User, dbProject: Project) => {
              ProjectDao.addScenesToProject(dbScenes map { _.id }, dbProject.id) flatMap {
                _ => {
                  ProjectScenesDao.listProjectScenes(dbProject.id, page, csq) map {
                    (paginatedResponse: PaginatedResponse[Scene.ProjectScene]) => (dbScenes, paginatedResponse.results)
                  }
                }
              }
            }
          }

          val (insertedScenes, listedScenes) = scenesListIO.transact(xa).unsafeRunSync
          val insertedIds = insertedScenes.toSet map { (scene: Scene.WithRelated) => scene.id }
          val listedIds = listedScenes.toSet map { (scene: Scene.ProjectScene) => scene.id }
          insertedIds == listedIds
        }
      }
    }
  }
}
