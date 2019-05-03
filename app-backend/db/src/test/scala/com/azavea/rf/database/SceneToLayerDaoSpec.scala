package com.rasterfoundry.database

import com.rasterfoundry.datamodel._
import com.rasterfoundry.common.Generators.Implicits._
import com.rasterfoundry.database.Implicits._

import com.rasterfoundry.datamodel.PageRequest

import doobie._, doobie.implicits._
import cats.implicits._
import doobie.postgres.implicits._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatest.prop.Checkers

class SceneToLayerDaoSpec
    extends FunSuite
    with Matchers
    with Checkers
    with DBTestConfig
    with PropTestHelpers {

  test("Insert scenes to a project and accept them") {
    check {
      forAll {
        (user: User.Create,
         org: Organization.Create,
         platform: Platform,
         project: Project.Create,
         scenes: List[Scene.Create],
         dsCreate: Datasource.Create,
         page: PageRequest,
         csq: CombinedSceneQueryParams) =>
          {
            val acceptedSceneAndStlIO = for {
              (dbUser, _, _, dbProject) <- insertUserOrgPlatProject(user,
                                                                    org,
                                                                    platform,
                                                                    project)
              datasource <- DatasourceDao.create(dsCreate.toDatasource(dbUser),
                                                 dbUser)
              scenesInsert <- (scenes map {
                fixupSceneCreate(dbUser, datasource, _)
              }).traverse(
                (scene: Scene.Create) => SceneDao.insert(scene, dbUser)
              )
              _ <- ProjectDao.addScenesToProject(scenesInsert map { _.id },
                                                 dbProject.id,
                                                 dbProject.defaultLayerId,
                                                 false)
              acceptedSceneCount <- SceneToLayerDao.acceptScenes(
                dbProject.defaultLayerId,
                scenesInsert map { _.id })
              stls <- SceneToLayerDao.query
                .filter(fr"project_layer_id = ${dbProject.defaultLayerId}")
                .list
            } yield (acceptedSceneCount, stls)

            val (acceptedSceneCount, stls) =
              acceptedSceneAndStlIO.transact(xa).unsafeRunSync

            acceptedSceneCount == scenes.length &&
            stls.length == scenes.length &&
            stls.filter(_.accepted).length == scenes.length
          }
      }
    }
  }

  test("Verify scenes are returned in correct order for mosaic definition") {
    check {
      forAll {
        (user: User.Create,
         org: Organization.Create,
         platform: Platform,
         project: Project.Create,
         scenes: List[Scene.Create],
         dsCreate: Datasource.Create,
         page: PageRequest,
         csq: CombinedSceneQueryParams) =>
          {

            val mdAndStpsIO = for {
              (dbUser, _, _, dbProject) <- insertUserOrgPlatProject(user,
                                                                    org,
                                                                    platform,
                                                                    project)
              datasource <- DatasourceDao.create(dsCreate.toDatasource(dbUser),
                                                 dbUser)
              scenesInsert <- (scenes map {
                fixupSceneCreate(dbUser, datasource, _)
              }).traverse(
                (scene: Scene.Create) => SceneDao.insert(scene, dbUser)
              )
              selectedSceneIds = scenesInsert.take(2) map { _.id }
              _ <- ProjectDao.addScenesToProject(scenesInsert map { _.id },
                                                 dbProject.id,
                                                 dbProject.defaultLayerId,
                                                 false)
              _ <- SceneToLayerDao.setManualOrder(dbProject.defaultLayerId,
                                                  scenesInsert map { _.id })
              mds <- SceneToLayerDao
                .getMosaicDefinition(dbProject.defaultLayerId,
                                     None,
                                     sceneIdSubset = selectedSceneIds)
                .compile
                .to[List]
              stls <- SceneToLayerDao.query
                .filter(fr"project_layer_id = ${dbProject.defaultLayerId}")
                .filter(selectedSceneIds.toNel map {
                  Fragments.in(fr"scene_id", _)
                })
                .list
            } yield (mds, stls, selectedSceneIds)

            val (mds, stls, _) =
              mdAndStpsIO.transact(xa).unsafeRunSync

            // Mapping of scene ids to scene order
            val sceneMap =
              stls.map(s => (s.sceneId, s.sceneOrder.getOrElse(-1))).toMap

            // List of scene orders, ordered by the mosaic definitions
            val sceneOrders = mds.map(md => sceneMap.getOrElse(md.sceneId, -1))

            // If the scenes are returned in the correct order,
            // the scene orders of the mosaic definitions will be in order
            sceneOrders.sameElements(sceneOrders.sorted)
          }
      }
    }
  }

}
