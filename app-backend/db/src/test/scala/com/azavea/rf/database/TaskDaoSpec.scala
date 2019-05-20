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
  test("listing some features") {
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
            val connIO: ConnectionIO[
              (
                  Task.TaskFeatureCollection,
                  GeoJsonCodec.PaginatedGeoJsonResponse[Task.TaskFeature]
              )
            ] =
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
                fetched <- TaskDao.listTasks(
                  TaskQueryParameters(),
                  PageRequest(0, 10, Map.empty)
                )
              } yield { (collection, fetched) }

            val (featureCollection, fetched) = connIO.transact(xa).unsafeRunSync
            assert(
              (featureCollection.features.toSet & fetched.features.toSet) == fetched.features.toSet,
              "Retrieved and inserted features should be the same"
            )
            true
          }
      }
    }
  }
  test("insert tasks from a task feature collection") {
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
                .map(_.toGeoJSONFeature(Nil))
                .toSet,
              "Retrieved and inserted features should be the same"
            )
            true
          }
      }
    }
  }

  test("geoJSON selection should work") {
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
            val connIO: ConnectionIO[Boolean] =
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
                _ <- TaskDao.getTaskWithActions(collection.features.head.id)
              } yield true

            connIO.transact(xa).unsafeRunSync
          }
      }
    }
  }

  test(
    "updating should append actions when statuses are different and delete should work"
  ) {
    check {
      forAll {
        (
            userCreate: User.Create,
            orgCreate: Organization.Create,
            platform: Platform,
            projectCreate: Project.Create,
            taskFeatureCreate1: Task.TaskFeatureCreate,
            taskFeatureCreate2: Task.TaskFeatureCreate
        ) =>
          {
            val connIO = for {
              (dbUser, _, _, dbProject) <- insertUserOrgPlatProject(
                userCreate,
                orgCreate,
                platform,
                projectCreate
              )
              collection <- TaskDao.insertTasks(
                Task.TaskFeatureCollectionCreate(
                  features =
                    List(fixupTaskFeatureCreate(taskFeatureCreate1, dbProject))
                ),
                dbUser
              )
              update <- TaskDao.updateTask(
                collection.features.head.id,
                fixupTaskFeatureCreate(taskFeatureCreate2, dbProject)
              )
              delete <- TaskDao.deleteTask(collection.features.head.id)
            } yield (update, delete)
            val (updateResult, deleteResult) = connIO.transact(xa).unsafeRunSync

            if (taskFeatureCreate1.properties.status == taskFeatureCreate2.properties.status) {
              updateResult.get.properties.actions.length should be(0)
            } else {
              updateResult.get.properties.actions.length should be(1)
            }

            deleteResult should be(1)
            true
          }
      }
    }
  }
}
