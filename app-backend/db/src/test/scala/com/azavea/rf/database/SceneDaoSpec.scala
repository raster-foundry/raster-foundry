package com.rasterfoundry.database

import com.rasterfoundry.common.datamodel._
import com.rasterfoundry.common.datamodel.Generators.Implicits._

import doobie.implicits._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatest.prop.Checkers

class SceneDaoSpec
    extends FunSuite
    with Matchers
    with Checkers
    with DBTestConfig
    with PropTestHelpers {
  test("list scenes") {
    xa.use(t => SceneDao.query.list.transact(t))
      .unsafeRunSync
      .length should be >= 0
  }

  test("insert a scene") {
    check {
      forAll {
        (user: User.Create, org: Organization.Create, scene: Scene.Create) =>
          {
            val sceneInsertIO = for {
              orgAndUser <- insertUserAndOrg(user, org)
              (dbOrg, dbUser) = orgAndUser
              datasource <- unsafeGetRandomDatasource
              fixedUpSceneCreate = fixupSceneCreate(dbUser, datasource, scene)
              sceneInsert <- SceneDao.insert(fixedUpSceneCreate, dbUser)
            } yield (fixedUpSceneCreate, sceneInsert)
            val (fixedUpSceneCreate, insertedScene) =
              xa.use(t => sceneInsertIO.transact(t)).unsafeRunSync

            assert(insertedScene.visibility == fixedUpSceneCreate.visibility,
                   "Visibilities match")
            assert(insertedScene.tags == fixedUpSceneCreate.tags, "Tags match")
            assert(
              insertedScene.sceneMetadata == fixedUpSceneCreate.sceneMetadata,
              "Scene metadatas match")
            assert(insertedScene.name == fixedUpSceneCreate.name, "Names match")
            assert(
              insertedScene.tileFootprint == fixedUpSceneCreate.tileFootprint,
              "Tile footprints match")
            assert(
              insertedScene.dataFootprint == fixedUpSceneCreate.dataFootprint,
              "Data footprints match")
            assert(
              insertedScene.metadataFiles == fixedUpSceneCreate.metadataFiles,
              "Metadata files match")
            assert(
              insertedScene.ingestLocation == fixedUpSceneCreate.ingestLocation,
              "Ingest locations match")
            assert(
              insertedScene.filterFields == fixedUpSceneCreate.filterFields,
              "Filter fields match")
            assert(
              insertedScene.statusFields == fixedUpSceneCreate.statusFields,
              "Status fields match")
            true
          }
      }
    }
  }

  test("maybe insert a scene") {
    check {
      forAll {
        (user: User.Create, org: Organization.Create, scene: Scene.Create) =>
          {
            val sceneInsertIO = for {
              orgAndUser <- insertUserAndOrg(user, org)
              (dbOrg, dbUser) = orgAndUser
              datasource <- unsafeGetRandomDatasource
              fixedUpSceneCreate = fixupSceneCreate(dbUser, datasource, scene)
              sceneInsert <- SceneDao.insertMaybe(fixedUpSceneCreate, dbUser)
            } yield (fixedUpSceneCreate, sceneInsert)
            val (fixedUpSceneCreate, insertedSceneO) =
              xa.use(t => sceneInsertIO.transact(t)).unsafeRunSync
            // our expectation is that this should succeed so this should be safe -- if it fails that indicates
            // something else was wrong
            val insertedScene = insertedSceneO.get

            assert(insertedScene.visibility == fixedUpSceneCreate.visibility,
                   "Visibilities match")
            assert(insertedScene.tags == fixedUpSceneCreate.tags, "Tags match")
            assert(
              insertedScene.sceneMetadata == fixedUpSceneCreate.sceneMetadata,
              "Scene metadatas match")
            assert(insertedScene.name == fixedUpSceneCreate.name, "Names match")
            assert(
              insertedScene.tileFootprint == fixedUpSceneCreate.tileFootprint,
              "Tile footprints match")
            assert(
              insertedScene.dataFootprint == fixedUpSceneCreate.dataFootprint,
              "Data footprints match")
            assert(
              insertedScene.metadataFiles == fixedUpSceneCreate.metadataFiles,
              "Metadata files match")
            assert(
              insertedScene.ingestLocation == fixedUpSceneCreate.ingestLocation,
              "Ingest locations match")
            assert(
              insertedScene.filterFields == fixedUpSceneCreate.filterFields,
              "Filter fields match")
            assert(
              insertedScene.statusFields == fixedUpSceneCreate.statusFields,
              "Status fields match")
            true
          }
      }
    }
  }

  test("update a scene") {
    check {
      forAll {
        (user: User.Create,
         org: Organization.Create,
         insertScene: Scene.Create,
         updateScene: Scene.Create) =>
          {
            val sceneUpdateIO = for {
              orgAndUser <- insertUserAndOrg(user, org)
              (dbOrg, dbUser) = orgAndUser
              datasource <- unsafeGetRandomDatasource
              fixedUpSceneCreate = fixupSceneCreate(dbUser,
                                                    datasource,
                                                    insertScene)
              sceneInsert <- SceneDao.insert(fixedUpSceneCreate, dbUser)
              fixedUpUpdateScene = fixupSceneCreate(
                dbUser,
                datasource,
                updateScene).toScene(dbUser).copy(id = sceneInsert.id)
              affectedRows <- SceneDao.update(fixedUpUpdateScene,
                                              sceneInsert.id,
                                              dbUser)
              endScene <- SceneDao.unsafeGetSceneById(sceneInsert.id)
            } yield (affectedRows, fixedUpUpdateScene, endScene)

            val (affectedRows, fixedUpUpdateScene, updatedScene) =
              xa.use(t => sceneUpdateIO.transact(t)).unsafeRunSync

            assert(affectedRows == 1, "Number of affected rows is correct")
            assert(updatedScene.visibility == fixedUpUpdateScene.visibility,
                   "Visibilities match")
            assert(updatedScene.tags == fixedUpUpdateScene.tags, "Tags match")
            assert(
              updatedScene.sceneMetadata == fixedUpUpdateScene.sceneMetadata,
              "Metadatas match")
            assert(updatedScene.name == fixedUpUpdateScene.name, "Names match")
            assert(
              updatedScene.tileFootprint == fixedUpUpdateScene.tileFootprint,
              "Tile footprints match")
            assert(
              updatedScene.dataFootprint == fixedUpUpdateScene.dataFootprint,
              "Data footprints match")
            assert(
              updatedScene.ingestLocation == fixedUpUpdateScene.ingestLocation,
              "Ingest locations match")
            assert(updatedScene.filterFields == fixedUpUpdateScene.filterFields,
                   "Filter fields match")
            assert(updatedScene.statusFields == fixedUpUpdateScene.statusFields,
                   "Status fields match")
            true
          }
      }
    }
  }
}
