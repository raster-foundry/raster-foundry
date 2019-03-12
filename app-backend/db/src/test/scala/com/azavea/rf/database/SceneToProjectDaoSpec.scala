package com.rasterfoundry.database

import com.rasterfoundry.common.datamodel._
import com.rasterfoundry.common.datamodel.Generators.Implicits._
import com.rasterfoundry.database.Implicits._

import com.lonelyplanet.akka.http.extensions.PageRequest

import doobie._, doobie.implicits._
import cats.implicits._
import doobie.postgres.implicits._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatest.prop.Checkers

class SceneToProjectDaoSpec
    extends FunSuite
    with Matchers
    with Checkers
    with DBTestConfig
    with PropTestHelpers {
  test("Insert scenes to projects and accept them") {
    check {
      forAll {
        (user: User.Create,
         platform: Platform,
         org: Organization.Create,
         project: Project.Create,
         scenes: List[Scene.Create],
         dsCreate: Datasource.Create,
         page: PageRequest,
         csq: CombinedSceneQueryParams) =>
          {
            val acceptedSceneAndStpIO = for {
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
                                                 false)
              acceptedSceneCount <- SceneToProjectDao.acceptScenes(
                dbProject.id,
                scenesInsert map { _.id })
              stps <- SceneToProjectDao.query
                .filter(fr"project_id = ${dbProject.id}")
                .list
            } yield (acceptedSceneCount, stps)

            val (acceptedSceneCount, stps) =
              xa.use(t => acceptedSceneAndStpIO.transact(t)).unsafeRunSync

            acceptedSceneCount == scenes.length &&
            stps.length == scenes.length &&
            stps.filter(_.accepted).length == scenes.length
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
                                                 false)
              _ <- SceneToProjectDao.setManualOrder(dbProject.id,
                                                    scenesInsert map { _.id })
              mds <- SceneToProjectDao
                .getMosaicDefinition(dbProject.id,
                                     None,
                                     sceneIdSubset = selectedSceneIds)
                .compile
                .to[List]
              stps <- SceneToProjectDao.query
                .filter(fr"project_id = ${dbProject.id}")
                .filter(selectedSceneIds.toNel map {
                  Fragments.in(fr"scene_id", _)
                })
                .list
            } yield (mds, stps, selectedSceneIds)

            val (mds, stps, _) =
              xa.use(t => mdAndStpsIO.transact(t)).unsafeRunSync

            // Mapping of scene ids to scene order
            val sceneMap =
              stps.map(s => (s.sceneId, s.sceneOrder.getOrElse(-1))).toMap

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
