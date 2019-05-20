package com.rasterfoundry.database

import com.rasterfoundry.datamodel._
import com.rasterfoundry.common.Generators.Implicits._

import cats.implicits._
import doobie.ConnectionIO
import doobie.implicits._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatestplus.scalacheck.Checkers
import com.typesafe.scalalogging.LazyLogging

class TaskDaoSpec
    extends FunSuite
    with Matchers
    with Checkers
    with DBTestConfig
    with LazyLogging
    with PropTestHelpers {
  test("insert a task from a task feature create") {
    check {
      forAll {
        (
            userCreate: User.Create,
            orgCreate: Organization.Create,
            platform: Platform,
            projectCreate: Project.Create,
            taskFeaturesCreate: Task.TaskFeatureCollectionCreate
        ) =>
          {
            val connIO: ConnectionIO[(Task.TaskFeatureCollection, List[Task])] =
              for {
                (dbUser, _, _, dbProject) <- insertUserOrgPlatProject(
                  userCreate,
                  orgCreate,
                  platform,
                  projectCreate
                )
                collection <- TaskDao.insertTasks(
                  fixupTaskFeaturesCollection(taskFeaturesCreate, dbProject),
                  dbUser
                )
                fetched <- collection.features traverse { feat =>
                  TaskDao.unsafeGetTaskById(feat.id)
                }
              } yield { (collection, fetched) }

            val (featureCollection, fetched) = connIO.transact(xa).unsafeRunSync
            assert(
              featureCollection.features.toSet == fetched
                .map(_.toGeoJSONFeature)
                .toSet,
              "Retrieved and inserted features should be the same"
            )
            true
          }
      }
    }
  }
}
