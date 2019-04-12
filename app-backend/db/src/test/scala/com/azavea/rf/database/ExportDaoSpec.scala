package com.rasterfoundry.database

import java.util.UUID

import com.rasterfoundry.common.ast.MapAlgebraAST
import com.rasterfoundry.common.ast.MapAlgebraAST.{LayerRaster, ProjectRaster}
import com.rasterfoundry.common.ast.codec.MapAlgebraCodec
import com.rasterfoundry.common.datamodel._
import com.rasterfoundry.common.datamodel.Generators.Implicits._
import com.rasterfoundry.database.Implicits._
import io.circe.syntax._
import doobie.implicits._
import org.scalatest._
import org.scalatest.prop.Checkers
import org.scalacheck.Prop.forAll

class ExportDaoSpec
    extends FunSuite
    with Matchers
    with Checkers
    with DBTestConfig
    with PropTestHelpers
    with MapAlgebraCodec {

  test("types") {
    xa.use(
        t => {
          ExportDao.query.list.transact(t)
        }
      )
      .unsafeRunSync
      .length should be >= 0
  }

  test("can create an export definition for project export") {
    check {
      forAll {
        (user: User.Create,
         org: Organization.Create,
         platform: Platform,
         projCreate: Project.Create,
         sceneCreate: Scene.Create,
         exportCreate: Export.Create) =>
          {
            val projectInsertIO = for {
              (dbUser, _, _, dbProject) <- insertUserOrgPlatProject(user,
                                                                    org,
                                                                    platform,
                                                                    projCreate)
              randomDatasource <- unsafeGetRandomDatasource
              dbScene <- {
                val scene =
                  fixupSceneCreate(dbUser, randomDatasource, sceneCreate)
                SceneDao.insert(scene, dbUser)
              }
              _ <- ProjectDao.addScenesToProject(List(dbScene.id),
                                                 dbProject.id,
                                                 dbProject.defaultLayerId)
              dbExport <- {
                val export = exportCreate.toExport(dbUser)
                ExportDao.insert(export.copy(projectId = Some(dbProject.id)),
                                 dbUser)
              }
              exportDefinition <- {
                ExportDao.getExportDefinition(dbExport, dbUser)
              }
            } yield exportDefinition

            xa.use(t => projectInsertIO.transact(t)).unsafeRunSync
            true
          }
      }
    }
  }

  test("can create an export definition for project _layer_ export") {
    check {
      forAll {
        (user: User.Create,
         org: Organization.Create,
         platform: Platform,
         projCreate: Project.Create,
         sceneCreate: Scene.Create,
         exportCreate: Export.Create) =>
          {
            val projectInsertIO = for {
              (dbUser, _, _, dbProject) <- insertUserOrgPlatProject(user,
                                                                    org,
                                                                    platform,
                                                                    projCreate)
              randomDatasource <- unsafeGetRandomDatasource
              dbScene <- {
                val scene =
                  fixupSceneCreate(dbUser, randomDatasource, sceneCreate)
                SceneDao.insert(scene, dbUser)
              }
              _ <- ProjectDao.addScenesToProject(List(dbScene.id),
                                                 dbProject.id,
                                                 dbProject.defaultLayerId)
              dbExport <- {
                val export = exportCreate.toExport(dbUser)
                ExportDao.insert(export.copy(projectId = Some(dbProject.id),
                                             projectLayerId =
                                               Some(dbProject.defaultLayerId)),
                                 dbUser)
              }
              exportDefinition <- {
                ExportDao.getExportDefinition(dbExport, dbUser)
              }
            } yield exportDefinition

            xa.use(t => projectInsertIO.transact(t)).unsafeRunSync
            true
          }
      }
    }
  }

  test(
    "can create an export definition for tool run with layer and project sources") {
    check {
      forAll {
        (user: User.Create,
         org: Organization.Create,
         platform: Platform,
         projCreate: Project.Create,
         sceneCreate: Scene.Create,
         exportCreate: Export.Create,
         toolRunCreate: ToolRun.Create) =>
          {
            val projectInsertIO = for {
              (dbUser, _, _, dbProject) <- insertUserOrgPlatProject(user,
                                                                    org,
                                                                    platform,
                                                                    projCreate)
              randomDatasource <- unsafeGetRandomDatasource
              dbScene <- {
                val scene =
                  fixupSceneCreate(dbUser, randomDatasource, sceneCreate)
                SceneDao.insert(scene, dbUser)
              }
              _ <- ProjectDao.addScenesToProject(List(dbScene.id),
                                                 dbProject.id,
                                                 dbProject.defaultLayerId)
              dbToolRun <- {
                val projectRaster: ProjectRaster =
                  ProjectRaster(UUID.randomUUID(),
                                dbProject.id,
                                Some(2),
                                None,
                                None)
                val layerRaster: LayerRaster =
                  LayerRaster(UUID.randomUUID(),
                              dbProject.defaultLayerId,
                              Some(1),
                              None,
                              None)
                val ast =
                  MapAlgebraAST
                    .Addition(List(projectRaster, layerRaster),
                              UUID.randomUUID(),
                              None)

                val toolRun =
                  toolRunCreate.copy(executionParameters = ast.asJson)
                ToolRunDao.insertToolRun(toolRun, dbUser)

              }
              dbExport <- {
                val export = exportCreate.toExport(dbUser)
                ExportDao.insert(export.copy(toolRunId = Some(dbToolRun.id)),
                                 dbUser)
              }
              exportDefinition <- ExportDao.getExportDefinition(dbExport,
                                                                dbUser)
            } yield exportDefinition

            xa.use(t => projectInsertIO.transact(t)).unsafeRunSync
            true
          }
      }
    }
  }
}
