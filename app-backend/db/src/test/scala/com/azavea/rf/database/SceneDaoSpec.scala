package com.azavea.rf.database

import com.azavea.rf.datamodel._
import com.azavea.rf.datamodel.Generators.Implicits._
import com.azavea.rf.database.Implicits._

import doobie._, doobie.implicits._
import cats._, cats.data._, cats.effect.IO
import cats.syntax.either._
import doobie.postgres._, doobie.postgres.implicits._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatest.prop.Checkers


class SceneDaoSpec extends FunSuite with Matchers with Checkers with DBTestConfig with PropTestHelpers {
  test("list scenes") {
    SceneDao.query.list.transact(xa).unsafeRunSync.length should be >= 0
  }

  test("insert a scene") {
    check {
      forAll {
        (user: User.Create, org: Organization.Create, scene: Scene.Create) => {
          val sceneInsertIO = for {
            orgAndUser <- insertUserAndOrg(user, org)
            (dbOrg, dbUser) = orgAndUser
            datasource <- unsafeGetRandomDatasource
            sceneInsert <- SceneDao.insert(fixupSceneCreate(dbUser, dbOrg, datasource, scene), dbUser)
          } yield sceneInsert
          val insertedScene = sceneInsertIO.transact(xa).unsafeRunSync

          insertedScene.visibility == scene.visibility &&
            insertedScene.tags == scene.tags &&
            insertedScene.sceneMetadata == scene.sceneMetadata &&
            insertedScene.name == scene.name &&
            insertedScene.tileFootprint == scene.tileFootprint &&
            insertedScene.dataFootprint == scene.dataFootprint &&
            insertedScene.metadataFiles == scene.metadataFiles &&
            insertedScene.ingestLocation == scene.ingestLocation &&
            insertedScene.filterFields == scene.filterFields &&
            insertedScene.statusFields == scene.statusFields
        }
      }
    }
  }

  test("maybe insert a scene") {
    check {
      forAll {
        (user: User.Create, org: Organization.Create, scene: Scene.Create) => {
          val sceneInsertIO = for {
            orgAndUser <- insertUserAndOrg(user, org)
            (dbOrg, dbUser) = orgAndUser
            datasource <- unsafeGetRandomDatasource
            sceneInsert <- SceneDao.insertMaybe(fixupSceneCreate(dbUser, dbOrg, datasource, scene), dbUser)
          } yield sceneInsert
          val insertedSceneO = sceneInsertIO.transact(xa).unsafeRunSync
          // our expectation is that this should succeed so this should be safe -- if it fails that indicates
          // something else was wrong
          val insertedScene = insertedSceneO.get

          insertedScene.visibility == scene.visibility &&
            insertedScene.tags == scene.tags &&
            insertedScene.sceneMetadata == scene.sceneMetadata &&
            insertedScene.name == scene.name &&
            insertedScene.tileFootprint == scene.tileFootprint &&
            insertedScene.dataFootprint == scene.dataFootprint &&
            insertedScene.metadataFiles == scene.metadataFiles &&
            insertedScene.ingestLocation == scene.ingestLocation &&
            insertedScene.filterFields == scene.filterFields &&
            insertedScene.statusFields == scene.statusFields
        }
      }
    }
  }

  test("update a scene") {
    check {
      forAll {
        (user: User.Create, org: Organization.Create, insertScene: Scene.Create, updateScene: Scene.Create) => {
          val sceneInsertWithUserOrgDatasourceIO = for {
            orgAndUser <- insertUserAndOrg(user, org)
            (dbOrg, dbUser) = orgAndUser
            datasource <- unsafeGetRandomDatasource
            sceneInsert <- SceneDao.insert(fixupSceneCreate(dbUser, dbOrg, datasource, insertScene), dbUser)
          } yield (sceneInsert, dbUser, dbOrg, datasource)

          val sceneUpdateWithSceneIO = sceneInsertWithUserOrgDatasourceIO flatMap {
            case (dbScene: Scene.WithRelated, dbUser: User, dbOrg: Organization, dbDatasource: Datasource) => {
              val sceneId = dbScene.id
              val fixedUpUpdateScene = fixupSceneCreate(dbUser, dbOrg, dbDatasource, updateScene)
                .toScene(dbUser)
                .copy(id = dbScene.id)
              SceneDao.update(fixedUpUpdateScene, sceneId, dbUser) flatMap {
                case (affectedRows: Int, _) => {
                  SceneDao.unsafeGetSceneById(sceneId, dbUser) map { (affectedRows, _) }
                }
              }
            }
          }

          val (affectedRows, updatedScene) = sceneUpdateWithSceneIO.transact(xa).unsafeRunSync

          affectedRows == 1 &&
            updatedScene.visibility == updateScene.visibility &&
            updatedScene.tags == updateScene.tags &&
            updatedScene.sceneMetadata == updateScene.sceneMetadata &&
            updatedScene.name == updateScene.name &&
            updatedScene.tileFootprint == updateScene.tileFootprint &&
            updatedScene.dataFootprint == updateScene.dataFootprint &&
            updatedScene.ingestLocation == updateScene.ingestLocation &&
            updatedScene.filterFields == updateScene.filterFields &&
            updatedScene.statusFields == updateScene.statusFields
        }
      }
    }
  }
}
