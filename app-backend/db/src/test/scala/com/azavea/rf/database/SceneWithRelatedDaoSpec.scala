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

class SceneWithRelatedDaoSpec extends FunSuite with Matchers with Checkers with DBTestConfig with PropTestHelpers {
  test("list scenes with related") {
    check {
      forAll {
        (user: User.Create, org: Organization.Create, page: PageRequest, csq: CombinedSceneQueryParams) => {
          val sceneListIO = for {
            orgAndUser <- insertUserAndOrg(user, org)
            (dbOrg, dbUser) = orgAndUser
            sceneList <- SceneWithRelatedDao.listScenes(page, csq, dbUser)
          } yield sceneList

          sceneListIO.transact(xa).unsafeRunSync.results.length >= 0
        }
      }
    }
  }

  test("list scenes for a project") {
    check {
      forAll {
        (user: User.Create, org: Organization.Create, project: Project.Create, scenes: List[Scene.Create],
         page: PageRequest, csq: CombinedSceneQueryParams) => {
          val scenesInsertWithUserProjectIO = for {
            orgUserProject <- insertUserOrgProject(user, org, project)
            (dbOrg, dbUser, dbProject) = orgUserProject
            datasource <- unsafeGetRandomDatasource
            scenesInsert <- (scenes map { fixupSceneCreate(dbUser, dbOrg, datasource, _) }).traverse(
              (scene: Scene.Create) => SceneDao.insert(scene, dbUser)
            )
          } yield (scenesInsert, dbUser, dbProject)

          val scenesListIO = scenesInsertWithUserProjectIO flatMap {
            case (dbScenes: List[Scene.WithRelated], dbUser: User, dbProject: Project) => {
              ProjectDao.addScenesToProject(dbScenes map { _.id }, dbProject.id, dbUser) flatMap {
                _ => {
                  SceneWithRelatedDao.listProjectScenes(dbProject.id, page, csq, dbUser) map {
                    (paginatedResponse: PaginatedResponse[Scene.WithRelated]) => (dbScenes, paginatedResponse.results)
                  }
                }
              }
            }
          }

          val (insertedScenes, listedScenes) = scenesListIO.transact(xa).unsafeRunSync
          val insertedIds = insertedScenes.toSet map { (scene: Scene.WithRelated) => scene.id }
          val listedIds = listedScenes.toSet map { (scene: Scene.WithRelated) => scene.id }
          insertedIds == listedIds
        }
      }
    }
  }

  test("get scenes to ingest") {
    check {
      forAll {
        (user: User.Create, org: Organization.Create, project: Project.Create, scenes: List[Scene.Create]) => {
          val scenesInsertWithUserProjectIO = for {
            orgUserProject <- insertUserOrgProject(user, org, project)
            (dbOrg, dbUser, dbProject) = orgUserProject
            datasource <- unsafeGetRandomDatasource
            scenesInsert <- (scenes map { fixupSceneCreate(dbUser, dbOrg, datasource, _) }).traverse(
              (scene: Scene.Create) => SceneDao.insert(scene, dbUser)
            )
          } yield (scenesInsert, dbUser, dbProject)

          val scenesToIngestIO = scenesInsertWithUserProjectIO flatMap {
            case (dbScenes: List[Scene.WithRelated], dbUser: User, dbProject: Project) => {
              ProjectDao.addScenesToProject(dbScenes map { _.id }, dbProject.id, dbUser) flatMap {
                _ => SceneWithRelatedDao.getScenesToIngest(dbProject.id) map {
                  (ingestableScenes: List[Scene.WithRelated]) => {
                    (dbScenes, ingestableScenes)
                  }
                }
              }
            }
          }

          val (insertedScenes, ingestableScenes) = scenesToIngestIO.transact(xa).unsafeRunSync
          val ingestableScenesIds = ingestableScenes map { _.id }
          val ingestableIdsFromInserted = insertedScenes.filter(
            (scene: Scene.WithRelated) => {
              val staleModified =
                scene.modifiedAt.before(Timestamp.valueOf(LocalDate.now().atStartOfDay.plusDays(-1L)))
              val ingestStatus = scene.statusFields.ingestStatus
              (staleModified, ingestStatus) match {
                case (true, IngestStatus.Ingesting) => true
                case (false, IngestStatus.Ingesting) => false
                case (_, IngestStatus.Ingested) => false
                case (_, _) => true
              }
            }).map( _.id ).toSet
          ingestableIdsFromInserted == ingestableScenesIds.toSet
        }
      }
    }
  }
}
