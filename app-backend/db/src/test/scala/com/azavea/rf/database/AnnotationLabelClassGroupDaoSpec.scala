package com.rasterfoundry.database

import com.rasterfoundry.common.Generators.Implicits._
import com.rasterfoundry.datamodel._

import cats.implicits._
import doobie._
import doobie.implicits._
import org.scalacheck.Prop.forAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.Checkers

import java.util.UUID

class AnnotationLabelClassGroupDaoSpec
    extends AnyFunSuite
    with Matchers
    with Checkers
    with DBTestConfig
    with PropTestHelpers {

  test("list annotations class groups for a project") {
    check {
      forAll(
        (
            userCreate: User.Create,
            annotationProjectCreate: AnnotationProject.Create
        ) => {
          val insertIO: ConnectionIO[
            (List[AnnotationLabelClassGroup], List[AnnotationLabelClassGroup])
          ] = for {
            user <- UserDao.create(userCreate)
            inserted <-
              AnnotationProjectDao
                .insert(annotationProjectCreate, user)
            listedReal <-
              AnnotationLabelClassGroupDao
                .listByProjectId(inserted.id)
            listedBogus <-
              AnnotationLabelClassGroupDao
                .listByProjectId(
                  UUID.randomUUID
                )
          } yield { (listedReal, listedBogus) }

          val (listedReal, listedBogus) = insertIO.transact(xa).unsafeRunSync

          val expectedNames =
            (annotationProjectCreate.labelClassGroups map { _.name }).toSet

          assert(
            expectedNames == (listedReal map { _.name }).toSet,
            "Listed names for project id match names of groups to create"
          )
          assert(
            Set.empty[String] == (listedBogus map { _.name }).toSet,
            "Bogus id lists no annotation label class groups"
          )
          true
        }
      )
    }
  }

  test("delete annotations class groups for a project") {
    check {
      forAll(
        (
            userCreate: User.Create,
            annotationProjectCreate: AnnotationProject.Create
        ) => {
          val insertIO: ConnectionIO[(Int, Int)] = for {
            user <- UserDao.create(userCreate)
            inserted <-
              AnnotationProjectDao
                .insert(annotationProjectCreate, user)
            deletedReal <-
              AnnotationLabelClassGroupDao
                .deleteByProjectId(inserted.id)
            deletedBogus <-
              AnnotationLabelClassGroupDao
                .deleteByProjectId(
                  UUID.randomUUID
                )
          } yield { (deletedReal, deletedBogus) }

          val (deletedReal, deletedBogus) = insertIO.transact(xa).unsafeRunSync

          assert(
            deletedReal == annotationProjectCreate.labelClassGroups.length,
            "Deleted all annotation label class groups for real project id"
          )
          assert(
            deletedBogus == 0,
            "Bogus id deletes no annotation label class groups"
          )

          true
        }
      )
    }
  }

  test("update annotation label class group") {
    check {
      forAll(
        (
            userCreate: User.Create,
            annotationProjectCreate: AnnotationProject.Create,
            campaignCreate: Campaign.Create,
            labelClassGroupCreate: AnnotationLabelClassGroup.Create
        ) => {
          val groupToUpdate = (campaign: Campaign) =>
            AnnotationLabelClassGroup(
              id = UUID.randomUUID,
              name = labelClassGroupCreate.name,
              annotationProjectId = None,
              campaignId = Some(campaign.id),
              index = labelClassGroupCreate.index.getOrElse(0)
            )
          val updateIO = for {
            user <- UserDao.create(userCreate)
            insertedProject <-
              AnnotationProjectDao
                .insert(annotationProjectCreate, user)
            classGroupOpt = insertedProject.labelClassGroups.headOption
            insertedCampaign <- CampaignDao.insertCampaign(
              campaignCreate.copy(parentCampaignId = None),
              user
            )
            updateGroup = groupToUpdate(insertedCampaign)
            _ <- classGroupOpt traverse { group =>
              AnnotationLabelClassGroupDao.update(
                group.id,
                updateGroup
              )
            }
            classGroupUpdatedOpt <- classGroupOpt flatTraverse { group =>
              AnnotationLabelClassGroupDao.getGroupWithClassesById(group.id)
            }
          } yield { (classGroupOpt, updateGroup, classGroupUpdatedOpt) }

          val (groupOpt, updateGroup, groupUpdatedOpt) =
            updateIO.transact(xa).unsafeRunSync

          assert(
            (groupOpt, groupUpdatedOpt).tupled match {
              case Some((group, groupUpdated)) =>
                group.annotationProjectId == groupUpdated.annotationProjectId &&
                  group.isActive == groupUpdated.isActive &&
                  groupUpdated.campaignId == updateGroup.campaignId &&
                  groupUpdated.index == updateGroup.index &&
                  groupUpdated.name == updateGroup.name
              case None if annotationProjectCreate.labelClassGroups.size == 0 =>
                true
              case _ => false
            },
            "Only name, index, and campaign id fields can be updated for label class group"
          )

          true
        }
      )
    }
  }

  test("deactivate then reactivate a label class group") {
    check {
      forAll(
        (
            userCreate: User.Create,
            annotationProjectCreate: AnnotationProject.Create
        ) => {
          val updateIO = for {
            user <- UserDao.create(userCreate)
            inserted <-
              AnnotationProjectDao
                .insert(annotationProjectCreate, user)
            classGroupOpt = inserted.labelClassGroups.headOption
            _ <- classGroupOpt traverse { group =>
              AnnotationLabelClassGroupDao.deactivate(group.id)
            }
            groupDeactivatedOpt <- classGroupOpt flatTraverse { group =>
              AnnotationLabelClassGroupDao.getGroupWithClassesById(group.id)
            }
            groupActivatedOpt <- classGroupOpt traverse { group =>
              AnnotationLabelClassGroupDao.activate(group.id)
            }
          } yield { (groupDeactivatedOpt, groupActivatedOpt) }

          val (deactivatedOpt, activatedOpt) =
            updateIO.transact(xa).unsafeRunSync

          assert(
            (deactivatedOpt, activatedOpt).tupled match {
              case Some((deactivated, activated)) =>
                !deactivated.isActive && activated.isActive
              case None if annotationProjectCreate.labelClassGroups.size == 0 =>
                true
              case _ => false
            },
            "Label class group activation and deactivation work"
          )

          true
        }
      )
    }
  }
}
