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

class SceneWithRelatedDaoSpec
    extends FunSuite
    with Matchers
    with Checkers
    with DBTestConfig
    with PropTestHelpers {

  test("list authorized scenes") {
    check {
      forAll {
        (user1: User.Create,
         user2: User.Create,
         org: Organization.Create,
         pageRequest: PageRequest,
         scenes1: List[Scene.Create],
         scenes2: List[Scene.Create],
         dsCreate1: Datasource.Create,
         dsCreate2: Datasource.Create) =>
          {
            val scenesIO = for {
              // quick and dirty fix for dev database. listedScenes being a paginated result makes things complicated otherwise
              _ <- fr"truncate scenes cascade".update.run
              dbUser1 <- UserDao.create(user1)
              dbUser2 <- UserDao.create(user2)
              datasource1 <- DatasourceDao.create(
                dsCreate1.toDatasource(dbUser1),
                dbUser1)
              datasource2 <- DatasourceDao.create(
                dsCreate2.toDatasource(dbUser2),
                dbUser2)
              dbScenes1 <- (scenes1 map { (scene: Scene.Create) =>
                fixupSceneCreate(dbUser1, datasource1, scene)
              }).traverse(
                (scene: Scene.Create) => SceneDao.insert(scene, dbUser1)
              )
              _ <- (scenes2 map { (scene: Scene.Create) =>
                fixupSceneCreate(dbUser2, datasource2, scene)
              }).traverse(
                (scene: Scene.Create) => SceneDao.insert(scene, dbUser2)
              )
              listedScenes <- SceneWithRelatedDao.listAuthorizedScenes(
                pageRequest,
                CombinedSceneQueryParams(),
                dbUser1)
            } yield { (dbScenes1, listedScenes) }

            val (insertedScenes, listedScenes) =
              scenesIO.transact(xa).unsafeRunSync
            val insertedNamesSet = insertedScenes.toSet map {
              (scene: Scene.WithRelated) =>
                scene.name
            }
            val listedNamesSet = listedScenes.results.toSet map {
              (scene: Scene.Browse) => scene.name
            }
            assert(
              listedNamesSet.intersect(insertedNamesSet) == listedNamesSet,
              "listed scenes should be a strict subset of inserted scenes by user 1")
            true
          }
      }
    }
  }

  test("get scenes to ingest") {
    check {
      forAll {
        (user: User.Create,
         org: Organization.Create,
         project: Project.Create,
         scenes: List[Scene.Create]) =>
          {
            val scenesInsertWithUserProjectIO = for {
              orgUserProject <- insertUserOrgProject(user, org, project)
              (dbOrg, dbUser, dbProject) = orgUserProject
              datasource <- unsafeGetRandomDatasource
              scenesInsert <- (scenes map {
                fixupSceneCreate(dbUser, datasource, _)
              }).traverse(
                (scene: Scene.Create) => SceneDao.insert(scene, dbUser)
              )
            } yield (scenesInsert, dbUser, dbProject)

            val scenesToIngestIO = for {
              scenesUserProject <- scenesInsertWithUserProjectIO
              (dbScenes, _, dbProject) = scenesUserProject
              _ <- ProjectDao.addScenesToProject(dbScenes map { _.id },
                                                 dbProject.id)
              retrievedScenes <- dbScenes traverse { scene =>
                SceneDao.unsafeGetSceneById(scene.id)
              }
            } yield { (dbScenes, retrievedScenes) }

            val (insertedScenes, scenesInProject) =
              scenesToIngestIO.transact(xa).unsafeRunSync
            val ingestableScenesIds = scenesInProject filter { scene =>
              (scene.statusFields.ingestStatus, scene.sceneType) match {
                case (_, Some(SceneType.COG)) => false
                case (IngestStatus.ToBeIngested, _) => true
                case _ => false
              }
            } map { _.id }
            val ingestableIdsFromInserted = insertedScenes
              .filter((scene: Scene.WithRelated) => {
                val staleModified =
                  scene.modifiedAt.before(Timestamp.valueOf(
                    LocalDate.now().atStartOfDay.plusDays(-1L)))
                val ingestStatus = scene.statusFields.ingestStatus
                (staleModified, ingestStatus, scene.sceneType) match {
                  case (true, IngestStatus.Ingesting, _)  => true
                  case (false, IngestStatus.Ingesting, _) => false
                  case (_, IngestStatus.Ingested, _)      => false
                  case (_, _, Some(SceneType.COG))        => false
                  case (_, _, _)                          => true
                }
              })
              .map(_.id)
              .toSet
            ingestableIdsFromInserted == ingestableScenesIds.toSet
          }
      }
    }
  }
}
