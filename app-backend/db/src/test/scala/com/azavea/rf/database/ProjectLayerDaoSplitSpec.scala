package com.rasterfoundry.database

import com.rasterfoundry.common.datamodel._
import com.rasterfoundry.common.datamodel.Generators.Implicits._

import doobie.implicits._
import cats.implicits._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatest.prop.Checkers

import java.util.UUID

class ProjectLayerDaoSplitSpec
    extends FunSuite
    with Matchers
    with Checkers
    with DBTestConfig
    with PropTestHelpers {

  def getSplitExpectations(splitOptions: SplitOptions) =
    (s2ls: Map[UUID, List[Scene]], defaultLayerId: UUID) => {
      val dsAssert: Option[Assertion] = splitOptions.splitOnDatasource flatMap {
        case true =>
          Some(
            assert((s2ls
                     .mapValues(scenes =>
                       (scenes map { _.datasource }).distinct.length)
                     .values
                     .max) == 1,
                   "No layer should have scenes from more than one datasource"))
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
            assert(
              presenceInDefault.values.foldLeft(false)(_ || _),
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
         datasources: List[Datasource.Create],
         scenes: List[Scene.Create],
         splitOptions: SplitOptions) =>
          // Create a project
          // fixup scenes with datasources (maybe write a tuple gen)
          // create those scenes
          // add them to the project
          // call split with splitOptions
          // get results into the form i want for getSplitExpectations
          // check expectations
          val expectations = getSplitExpectations(splitOptions)
          true
      }
    }
  }

}
