package com.rasterfoundry.database

import com.rasterfoundry.common.Generators.Implicits._
import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.datamodel._

import cats.implicits._
import doobie.implicits._
import doobie.postgres.implicits._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatestplus.scalacheck.Checkers

import java.util.UUID
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ProjectLayerSplitSpec
    extends AnyFunSuite
    with Matchers
    with Checkers
    with DBTestConfig
    with PropTestHelpers {

  def getSplitExpectations(splitOptions: SplitOptions) =
    (s2ls: Map[UUID, List[Scene]], defaultLayerId: UUID) => {
      val dsAssert: Option[Assertion] = splitOptions.splitOnDatasource flatMap {
        case true =>
          val distinctDSes = s2ls
            .filterKeys(_ != defaultLayerId)
            .mapValues(scenes => (scenes map { _.datasource }).distinct.length)
            .values
          val comparison = if (distinctDSes.isEmpty) 0 else distinctDSes.max

          Some(
            assert(
              comparison <= 1,
              "No non-default layer should have scenes from more than one datasource"))
        case _ =>
          None
      }

      val removeAssert: Option[Assertion] = {
        // getOrElse just to collapse to a list, which will be more useful in the next step
        val defaultLayerScenes = s2ls.get(defaultLayerId).getOrElse(List.empty)
        val presenceInDefault = s2ls
          .filterKeys(_ != defaultLayerId)
          .mapValues(scenes => scenes.exists(defaultLayerScenes.contains(_)))
        splitOptions.removeFromLayer map {
          case true =>
            assert(
              !presenceInDefault.values.foldLeft(false)(_ || _),
              "No scene remaining in the default layer should be in any split layer")
          case _ =>
            // It's possible that the split options won't do any splits, in which case we won't
            // have any non-default layers for this fold left to do work on. In that case, we'll
            // only have the defaultLayerId in the map, so the size will be 1
            assert(
              presenceInDefault.values
                .foldLeft(false)(_ || _) || s2ls.size == 1,
              "Scenes in layers must not have been removed from the default layer")
        }
      }
      List(dsAssert, removeAssert).flatten
    }

  test("split should behave") {
    check {
      forAll {
        (user: User.Create,
         project: Project.Create,
         scenesWithDatasources: List[(Datasource.Create, Scene.Create)],
         splitOptions: SplitOptions) =>
          {
            // get results into the form i want for getSplitExpectations
            // check expectations
            val expectations = getSplitExpectations(splitOptions)
            val sceneMapIO = for {
              dbUser <- UserDao.create(user)
              dbProject <- ProjectDao.insertProject(project, dbUser)
              dbScenes <- scenesWithDatasources traverse {
                case (dsCreate, sceneCreate) =>
                  fixupDatasource(dsCreate, dbUser) flatMap { ds =>
                    SceneDao.insert(fixupSceneCreate(dbUser, ds, sceneCreate),
                                    dbUser)
                  }
              }
              _ <- ProjectDao.addScenesToProject(dbScenes map { _.id },
                                                 dbProject.id,
                                                 dbProject.defaultLayerId,
                                                 true)
              layerIds <- ProjectLayerDao.splitProjectLayer(
                dbProject.id,
                dbProject.defaultLayerId,
                splitOptions) map { layers =>
                dbProject.defaultLayerId +: (layers map { _.id })
              }
              sceneMap <- layerIds traverse { projectLayerId =>
                ProjectLayerScenesDao.query
                  .filter(fr"project_layer_id=$projectLayerId")
                  .list map { sceneList =>
                  Map(projectLayerId -> sceneList)
                }
              } map { sceneMaps =>
                sceneMaps.toNel map { _.reduce } getOrElse { Map.empty }
              }
            } yield (sceneMap, dbProject.defaultLayerId)

            val (sceneMap, defaultId) = sceneMapIO.transact(xa).unsafeRunSync
            expectations(sceneMap, defaultId)
            true
          }
      }
    }
  }

}
