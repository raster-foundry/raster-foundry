package com.rasterfoundry.database

import com.rasterfoundry.common.Generators.Implicits._
import com.rasterfoundry.common.ast.MapAlgebraAST
import com.rasterfoundry.common.ast.MapAlgebraAST.{LayerRaster, ProjectRaster}
import com.rasterfoundry.common.ast.codec.MapAlgebraCodec
import com.rasterfoundry.datamodel._

import doobie.implicits._
import io.circe.syntax._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatestplus.scalacheck.Checkers

import java.util.UUID

class ExportDaoSpec
    extends FunSuite
    with Matchers
    with Checkers
    with DBTestConfig
    with PropTestHelpers
    with MapAlgebraCodec {

  test("types") {

    ExportDao.query.list.transact(xa).unsafeRunSync.length should be >= 0
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
                ExportDao.getExportDefinition(dbExport)
              }
            } yield exportDefinition

            projectInsertIO.transact(xa).unsafeRunSync
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
                ExportDao.getExportDefinition(dbExport)
              }
            } yield exportDefinition

            projectInsertIO.transact(xa).unsafeRunSync
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
              exportDefinition <- ExportDao.getExportDefinition(dbExport)
            } yield exportDefinition

            projectInsertIO.transact(xa).unsafeRunSync
            true
          }
      }
    }
  }
}
