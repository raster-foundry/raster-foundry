package com.rasterfoundry.database

import com.rasterfoundry.common.Generators.Implicits._
import com.rasterfoundry.datamodel._

import cats.implicits._
import doobie.implicits._
import org.scalacheck.Prop.forAll
import org.scalatestplus.scalacheck.Checkers
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ProjectLayerDatasourceDaoSpec
    extends AnyFunSuite
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
            platform: Platform,
            project: Project.Create,
            scenes: List[Scene.Create],
            dsCreate: Datasource.Create
        ) =>
          {
            val datasourcesIO = for {
              (dbUser, _, _, dbProject) <- insertUserOrgPlatProject(
                user,
                org,
                platform,
                project
              )
              datasource <- fixupDatasource(dsCreate, dbUser)
              dbScenes <- (scenes map {
                fixupSceneCreate(dbUser, datasource, _)
              }).traverse(
                (scene: Scene.Create) => SceneDao.insert(scene, dbUser)
              )
              _ <- ProjectDao.addScenesToProject(
                dbScenes map { _.id },
                dbProject.id,
                dbProject.defaultLayerId
              )
              layerDatsources <- ProjectLayerDatasourcesDao
                .listProjectLayerDatasources(
                  dbProject.defaultLayerId
                )
            } yield (dbScenes.map(_.datasource.id), layerDatsources.map(_.id))

            val (insertedDatasourceIds, listedDatasourceIds) =
              datasourcesIO.transact(xa).unsafeRunSync

            assert(
              insertedDatasourceIds.toSet == listedDatasourceIds.toSet,
              "Listed datasources should be the same as that of scenes in this layer"
            )
            assert(
              insertedDatasourceIds.toSet.size == listedDatasourceIds.length,
              "Listed datasources length should be the same as deduplicated list of scene datasources"
            )
            true
          }
      }
    }
  }
}
