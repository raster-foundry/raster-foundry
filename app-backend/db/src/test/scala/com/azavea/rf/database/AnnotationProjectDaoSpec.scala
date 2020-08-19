package com.rasterfoundry.database

import com.rasterfoundry.common.Generators.Implicits._
import com.rasterfoundry.datamodel._

import cats.implicits._
import doobie.implicits._
import org.scalacheck.Prop.forAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.Checkers

import java.time.Instant
import java.sql.Timestamp

class AnnotationProjectDaoSpec
    extends AnyFunSuite
    with Matchers
    with Checkers
    with DBTestConfig
    with PropTestHelpers {
  test("inserting an annotation project") {
    check {
      forAll(
        (
            userCreate: User.Create,
            annotationProjectCreate: AnnotationProject.Create
        ) => {
          val insertIO = for {
            user <- UserDao.create(userCreate)
            inserted <- AnnotationProjectDao
              .insert(annotationProjectCreate, user)
          } yield inserted

          val result = insertIO.transact(xa).unsafeRunSync

          assert(
            result.tileLayers.length == annotationProjectCreate.tileLayers.length,
            "All the tile layers were inserted"
          )
          assert(
            result.labelClassGroups.length == annotationProjectCreate.labelClassGroups.length,
            "All the annotation class groups were inserted"
          )
          assert(
            result.name == annotationProjectCreate.name &&
              result.projectType == annotationProjectCreate.projectType &&
              result.taskSizePixels == annotationProjectCreate.taskSizePixels &&
              result.taskSizeMeters == None &&
              result.aoi == annotationProjectCreate.aoi &&
              result.labelersTeamId == annotationProjectCreate.labelersTeamId &&
              result.validatorsTeamId == annotationProjectCreate.validatorsTeamId &&
              result.projectId == annotationProjectCreate.projectId,
            "Created project respects data from project to create"
          )

          true
        }
      )
    }
  }

  test("list annotation projects") {
    check {
      forAll(
        (
            userCreate: User.Create,
            annotationProjectCreates: List[AnnotationProject.Create],
            taskFeaturesCreate: Task.TaskFeatureCollectionCreate
        ) => {
          val pageSize = 20
          val pageRequest = PageRequest(0, pageSize, Map.empty)

          val listIO = for {
            user <- UserDao.create(userCreate)
            insertedProjects <- annotationProjectCreates
              .take(pageSize) traverse { toInsert =>
              AnnotationProjectDao.insert(toInsert, user)
            }
            _ <- insertedProjects traverse { project =>
              TaskDao.insertTasks(
                fixupTaskFeaturesCollection(
                  taskFeaturesCreate,
                  project,
                  Some(TaskStatus.Unlabeled)
                ),
                user
              )
            }
            listed <- AnnotationProjectDao
              .listProjects(
                pageRequest,
                AnnotationProjectQueryParameters(),
                user
              )
          } yield (listed, insertedProjects)

          val (listedProjects, dbProjects) = listIO.transact(xa).unsafeRunSync

          val expectedNames = (dbProjects.take(pageSize) map { _.id }).toSet

          assert(
            expectedNames == (listedProjects.results map { _.id }).toSet,
            "Listed projects are those expected from project insertion"
          )

          true
        }
      )
    }
  }

  test("list unlabeled annotation projects") {
    check {
      forAll(
        (
            userCreate: User.Create,
            annotationProjectCreates: List[AnnotationProject.Create],
            taskFeaturesCreate: Task.TaskFeatureCollectionCreate,
            annotationProjectCreate: AnnotationProject.Create
        ) => {
          val pageSize = 20
          val pageRequest = PageRequest(0, pageSize, Map.empty)

          val listIO = for {
            user <- UserDao.create(userCreate)
            insertedProjects <- annotationProjectCreates
              .take(pageSize) traverse { toInsert =>
              AnnotationProjectDao.insert(toInsert, user)
            }
            _ <- insertedProjects traverse { project =>
              TaskDao.insertTasks(
                fixupTaskFeaturesCollection(
                  taskFeaturesCreate,
                  project,
                  Some(TaskStatus.Unlabeled)
                ),
                user
              )
            }
            insertedProject <- AnnotationProjectDao
              .insert(annotationProjectCreate, user)
            _ <- TaskDao.insertTasks(
              fixupTaskFeaturesCollection(
                taskFeaturesCreate,
                insertedProject,
                Some(TaskStatus.Labeled)
              ),
              user
            )
            listed <- AnnotationProjectDao
              .listProjects(
                pageRequest,
                AnnotationProjectQueryParameters(
                  projectFilterParams = AnnotationProjectFilterQueryParameters(
                    taskStatusesInclude = Seq(TaskStatus.Unlabeled)
                  )
                ),
                user
              )
          } yield { (listed, insertedProjects) }

          val (projects, unlabeledProjects) =
            listIO.transact(xa).unsafeRunSync

          val expectedIds = (unlabeledProjects map { _.id }).toSet

          val listedProjectIds = (projects.results map { _.id }).toSet

          assert(
            expectedIds == listedProjectIds,
            "Listed projects are those expected from project insertion with labeled project excluded"
          )

          true
        }
      )
    }
  }

  test("list share counts for user") {
    check {
      forAll(
        (
            userSharingCreate: User.Create,
            userSharedCreate: User.Create,
            annotationProjectSharedCreates: List[AnnotationProject.Create],
            annotationProjectUnsharedCreates: List[AnnotationProject.Create]
        ) => {
          val io = for {
            userSharing <- UserDao.create(userSharingCreate)
            userShared <- UserDao.create(userSharedCreate)
            sharedProjects <- annotationProjectSharedCreates traverse {
              toInsert =>
                AnnotationProjectDao.insert(toInsert, userSharing)
            }
            unsharedProjects <- annotationProjectUnsharedCreates traverse {
              toInsert =>
                AnnotationProjectDao.insert(toInsert, userSharing)
            }
            _ <- sharedProjects traverse { project =>
              AnnotationProjectDao.addPermission(
                project.id,
                ObjectAccessControlRule(
                  SubjectType.User,
                  Some(userShared.id),
                  ActionType.View
                )
              )
            }
            shareCounts <- AnnotationProjectDao.getAllShareCounts(
              userSharing.id
            )
          } yield (sharedProjects, unsharedProjects, shareCounts)

          val (sharedProjects, unsharedProjects, shareCounts) =
            io.transact(xa).unsafeRunSync
          assert(
            shareCounts.filter(sc => sc._2 > 0).size == sharedProjects.size,
            "Shared projects are counted correctly"
          )
          assert(
            shareCounts.filter(sc => sc._2 == 0).size == unsharedProjects.size,
            "Unshared projects are counted correctly"
          )
          assert(
            shareCounts.toList
              .map(_._2)
              .filter(_ == 1)
              .size == sharedProjects.size,
            "Shared projects have correct share count"
          )

          true
        }
      )
    }
  }

  test("get an annotation project by id") {
    check {
      forAll(
        (
            userCreate: User.Create,
            annotationProjectCreate: AnnotationProject.Create
        ) => {
          val insertIO = for {
            user <- UserDao.create(userCreate)
            inserted <- AnnotationProjectDao
              .insert(annotationProjectCreate, user)
            fetched <- AnnotationProjectDao.getById(inserted.id)
          } yield { (inserted, fetched) }

          val (inserted, Some(fetched)) = insertIO.transact(xa).unsafeRunSync

          assert(
            inserted.toProject == fetched,
            "Fetched project matches the project we inserted"
          )

          true
        }
      )
    }
  }

  test("update annotation projects") {
    check {
      forAll(
        (
            userCreate: User.Create,
            annotationProjectCreate: AnnotationProject.Create,
            annotationProjectUpdate: AnnotationProject.Create
        ) => {
          val updateIO = for {
            user <- UserDao.create(userCreate)
            inserted1 <- AnnotationProjectDao
              .insert(annotationProjectCreate, user)
            inserted2 <- AnnotationProjectDao
              .insert(annotationProjectUpdate, user)
            _ <- AnnotationProjectDao.update(inserted2.toProject, inserted1.id)
            fetched <- AnnotationProjectDao.getById(inserted1.id)
          } yield fetched

          val Some(afterUpdate) = updateIO.transact(xa).unsafeRunSync

          assert(
            afterUpdate.name == annotationProjectUpdate.name,
            "Name was updated"
          )
          assert(
            afterUpdate.labelersTeamId == annotationProjectUpdate.labelersTeamId,
            "Labelers were updated"
          )
          assert(
            afterUpdate.validatorsTeamId == annotationProjectUpdate.validatorsTeamId,
            "Validators were updated"
          )

          assert(
            afterUpdate.status == annotationProjectUpdate.status,
            "Readiness was updated"
          )

          assert(
            afterUpdate.campaignId == annotationProjectUpdate.campaignId,
            "Readiness was updated"
          )

          assert(
            afterUpdate.capturedAt == annotationProjectUpdate.capturedAt,
            "Readiness was updated"
          )

          true
        }
      )
    }
  }

  test("delete an annotation project") {
    check {
      forAll(
        (
            userCreate: User.Create,
            annotationProjectCreate: AnnotationProject.Create
        ) => {
          val deleteIO = for {
            user <- UserDao.create(userCreate)
            inserted <- AnnotationProjectDao
              .insert(annotationProjectCreate, user)
            deleted <- AnnotationProjectDao.deleteById(inserted.id, user)
            fetched <- AnnotationProjectDao.getById(inserted.id)
          } yield { (deleted, fetched) }

          val (count, result) = deleteIO.transact(xa).unsafeRunSync

          assert(count == 1, "One project was deleted")
          assert(
            result == Option.empty[AnnotationProject],
            "After deletion the project was gone"
          )

          true
        }
      )
    }
  }

  test("list projects belonging to a campaign") {
    check {
      forAll(
        (
            userCreate: User.Create,
            annotationProjectCreates1: List[AnnotationProject.Create],
            annotationProjectCreates2: List[AnnotationProject.Create],
            campaignCreate: Campaign.Create
        ) => {
          val pageSize = 20
          val pageRequest = PageRequest(0, pageSize * 2, Map.empty)

          val listIO = for {
            user <- UserDao.create(userCreate)
            insertedCampaign <- CampaignDao
              .insertCampaign(
                campaignCreate.copy(parentCampaignId = None),
                user
              )
            insertedProjects <- annotationProjectCreates1
              .take(pageSize) traverse { toInsert =>
              AnnotationProjectDao
                .insert(
                  toInsert.copy(campaignId = Some(insertedCampaign.id)),
                  user
                )
            }
            _ <- annotationProjectCreates2
              .take(pageSize) traverse { toInsert =>
              AnnotationProjectDao.insert(toInsert, user)
            }
            listed <- AnnotationProjectDao
              .listProjects(
                pageRequest,
                AnnotationProjectQueryParameters(
                  campaignId = Some(insertedCampaign.id)
                ),
                user
              )
          } yield (listed, insertedProjects)

          val (listedProjects, dbProjects) = listIO.transact(xa).unsafeRunSync

          val expectedIds = (dbProjects.take(pageSize) map { _.id }).toSet

          assert(
            expectedIds == (listedProjects.results map { _.id }).toSet,
            "Listed projects are those expected from projects belonging to an inserted campaign"
          )

          true
        }
      )
    }
  }

  test("list projects by capture timestamp") {
    check {
      forAll(
        (
            userCreate: User.Create,
            annotationProjectCreates1: List[AnnotationProject.Create],
            annotationProjectCreates2: List[AnnotationProject.Create]
        ) => {
          val pageSize = 20
          val pageRequest = PageRequest(0, pageSize * 2, Map.empty)
          val capturedAt = Timestamp.from(Instant.now());

          val listIO = for {
            user <- UserDao.create(userCreate)
            insertedProjects <- annotationProjectCreates1
              .take(pageSize) traverse { toInsert =>
              AnnotationProjectDao
                .insert(toInsert.copy(capturedAt = Some(capturedAt)), user)
            }
            _ <- annotationProjectCreates2
              .take(pageSize) traverse { toInsert =>
              AnnotationProjectDao
                .insert(toInsert, user)
            }
            listed <- AnnotationProjectDao
              .listProjects(
                pageRequest,
                AnnotationProjectQueryParameters(
                  capturedAt = Some(capturedAt)
                ),
                user
              )
          } yield (listed, insertedProjects)

          val (listedProjects, dbProjects) =
            listIO.transact(xa).unsafeRunSync

          assert(
            listedProjects.count == dbProjects.size,
            "Listed projects are those with updated capture timestamp"
          )

          true
        }
      )
    }
  }
}
