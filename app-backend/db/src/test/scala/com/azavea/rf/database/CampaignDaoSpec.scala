package com.rasterfoundry.database

import com.rasterfoundry.common.Generators.Implicits._
import com.rasterfoundry.datamodel._

import cats.implicits._
import doobie.implicits._
import org.scalacheck.Prop.forAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.Checkers

class CampaignDaoSpec
    extends AnyFunSuite
    with Matchers
    with Checkers
    with DBTestConfig
    with PropTestHelpers {
  test("insert a campaign") {
    check {
      forAll(
        (
            userCreate: User.Create,
            campaignCreate: Campaign.Create
        ) => {
          val insertIO = for {
            user <- UserDao.create(userCreate)
            inserted <- CampaignDao
              .insertCampaign(
                campaignCreate.copy(parentCampaignId = None),
                user
              )
          } yield (inserted, user)

          val (insertedCampaign, insertedUser) =
            insertIO.transact(xa).unsafeRunSync

          assert(
            insertedCampaign.name == campaignCreate.name,
            "Inserted campaign name is correct"
          )
          assert(
            insertedCampaign.campaignType == campaignCreate.campaignType,
            "Inserted campaign type is correct"
          )
          assert(
            insertedCampaign.owner == insertedUser.id,
            "Inserted campaign owner is correct"
          )
          assert(
            insertedCampaign.childrenCount == 0,
            "Inserted campaign has no children, yay"
          )
          true
        }
      )
    }
  }

  test("list campaigns") {
    check {
      forAll(
        (
            userCreate: User.Create,
            campaignCreates: List[Campaign.Create]
        ) => {
          val pageSize = 30
          val pageRequest = PageRequest(0, pageSize, Map.empty)

          val listIO = for {
            user <- UserDao.create(userCreate)
            insertedCampaigns <- campaignCreates
              .take(pageSize) traverse { toInsert =>
              CampaignDao
                .insertCampaign(toInsert.copy(parentCampaignId = None), user)
            }
            listed <- CampaignDao
              .listCampaigns(
                pageRequest,
                CampaignQueryParameters(),
                user
              )
          } yield (listed, insertedCampaigns)

          val (listedCampaigns, dbCampaigns) = listIO.transact(xa).unsafeRunSync

          val expectedIds = (dbCampaigns.take(pageSize) map { _.id }).toSet

          assert(
            expectedIds == (listedCampaigns.results map { _.id }).toSet,
            "Listed campaigns are those expected from campaign insertion"
          )

          true
        }
      )
    }
  }

  test("get a campaign by id") {
    check {
      forAll(
        (
            userCreate: User.Create,
            campaignCreate: Campaign.Create
        ) => {
          val getIO = for {
            user <- UserDao.create(userCreate)
            inserted <- CampaignDao
              .insertCampaign(
                campaignCreate.copy(parentCampaignId = None),
                user
              )
            fetched <- CampaignDao
              .getCampaignById(inserted.id)
          } yield (inserted, fetched)

          val (dbCampaign, Some(fetchedCampaign)) =
            getIO.transact(xa).unsafeRunSync

          assert(
            dbCampaign == fetchedCampaign,
            "Inserted campaign matched the fetched campaign"
          )

          true
        }
      )
    }
  }

  test("update a campaign") {
    check {
      forAll(
        (
            userCreate: User.Create,
            campaignCreate: Campaign.Create,
            campaignCreateUpdate: Campaign.Create
        ) => {
          val updateIO = for {
            user <- UserDao.create(userCreate)
            inserted1 <- CampaignDao
              .insertCampaign(
                campaignCreate.copy(parentCampaignId = None),
                user
              )
            inserted2 <- CampaignDao
              .insertCampaign(
                campaignCreateUpdate.copy(parentCampaignId = None),
                user
              )
            _ <- CampaignDao.updateCampaign(inserted2, inserted1.id)
            fetched <- CampaignDao.getCampaignById(inserted1.id)
          } yield fetched

          val Some(afterUpdate) = updateIO.transact(xa).unsafeRunSync

          assert(
            afterUpdate.name == campaignCreateUpdate.name,
            "Name was updated"
          )
          true
        }
      )
    }
  }

  test("delete a campaign") {
    check {
      forAll(
        (
            userCreate: User.Create,
            campaignCreate: Campaign.Create
        ) => {
          val deleteIO = for {
            user <- UserDao.create(userCreate)
            inserted <- CampaignDao
              .insertCampaign(
                campaignCreate.copy(parentCampaignId = None),
                user
              )
            deleted <- CampaignDao.deleteCampaign(inserted.id, user)
            fetched <- CampaignDao.getCampaignById(inserted.id)
          } yield { (deleted, fetched) }

          val (count, result) = deleteIO.transact(xa).unsafeRunSync

          assert(count == 1, "A campaign was removed")
          assert(
            result == Option.empty[Campaign],
            "The inserted campaign was gone after deletion"
          )
          true
        }
      )
    }
  }

  test("copy a campaign for users") {
    check {
      forAll(
        (
            userCreates: List[User.Create],
            userCreate: User.Create,
            campaignCreate: Campaign.Create,
            annotationProjectCreate: AnnotationProject.Create
        ) => {
          val copyIO = for {
            parent <- UserDao.create(userCreate)
            children <- userCreates traverse { u =>
              UserDao.create(u)
            }
            insertedCampaign <- CampaignDao
              .insertCampaign(
                campaignCreate.copy(parentCampaignId = None),
                parent
              )
            insertedProject <- AnnotationProjectDao
              .insert(
                annotationProjectCreate.copy(
                  campaignId = Some(insertedCampaign.id),
                  status = AnnotationProjectStatus.Waiting
                ),
                parent
              )
            _ <- AnnotationProjectDao.update(
              insertedProject.toProject
                .copy(status = AnnotationProjectStatus.Ready),
              insertedProject.id
            )
            campaignCopies <- children traverse { child =>
              CampaignDao.copyCampaign(insertedCampaign.id, child)
            }
            insertedCampaignAfterCopy <- CampaignDao.unsafeGetCampaignById(
              insertedCampaign.id
            )
            projectCopies <- (campaignCopies traverse { c =>
              AnnotationProjectDao.listByCampaign(c.id)
            }) map (_.flatten)
          } yield {
            (
              insertedCampaignAfterCopy,
              insertedProject,
              campaignCopies,
              projectCopies
            )
          }

          val (
            originalCampaign,
            originalProject,
            copiedCampaigns,
            copiedProjects
          ) = copyIO.transact(xa).unsafeRunSync

          assert(
            originalCampaign.childrenCount == userCreates.size,
            "Original campaign's children count is the same as the number of users to be added"
          )

          userCreates.size match {
            case 0 => true
            case _ =>
              assert(
                Set(originalCampaign.name) == copiedCampaigns.map(_.name).toSet,
                "Copy of the campaign worked"
              )
              assert(
                Set(originalProject.name) == copiedProjects.map(_.name).toSet,
                "Copy of the project worked"
              )
              assert(
                copiedCampaigns.map(c => Some(c.id)).toSet == copiedProjects
                  .map(_.campaignId)
                  .toSet,
                "Copy of the project has the id from the copied campaign"
              )
              assert(
                Set(Some(originalCampaign.id)) == copiedCampaigns
                  .map(_.parentCampaignId)
                  .toSet,
                "Copy of the campaign has the parent campaign id"
              )
              true
          }
        }
      )
    }
  }

  test("copy a campaign with new tags") {
    check {
      forAll(
        (
            userCreate: User.Create,
            campaignCreate: Campaign.Create,
            annotationProjectCreate: AnnotationProject.Create,
            clone: Campaign.Clone
        ) => {
          val copyIO = for {
            user <- UserDao.create(userCreate)
            insertedCampaign <- CampaignDao
              .insertCampaign(
                campaignCreate.copy(parentCampaignId = None),
                user
              )
            insertedProject <- AnnotationProjectDao
              .insert(
                annotationProjectCreate.copy(
                  campaignId = Some(insertedCampaign.id)
                ),
                user
              )
            campaignCopy <- CampaignDao
              .copyCampaign(insertedCampaign.id, user, Some(clone.tags))
            projectCopy <- AnnotationProjectDao.listByCampaign(campaignCopy.id)
          } yield {
            (insertedCampaign, insertedProject, campaignCopy, projectCopy)
          }

          val (
            originalCampaign,
            originalProject,
            copiedCampaign,
            copiedProject
          ) = copyIO.transact(xa).unsafeRunSync

          assert(
            originalCampaign.name == copiedCampaign.name,
            "Copy of the campaign worked"
          )
          assert(
            Set(originalProject.name) == copiedProject.map(_.name).toSet,
            "Copy of the project worked"
          )
          assert(
            Set(Some(copiedCampaign.id)) == copiedProject
              .map(_.campaignId)
              .toSet,
            "Copy of the project has the id from the copied campaign"
          )
          assert(
            Some(originalCampaign.id) == copiedCampaign.parentCampaignId,
            "Copy of the campaign has the parent campaign id"
          )
          assert(
            clone.tags.toSet == copiedCampaign.tags.toSet,
            "Copy of the campaign has the given tags"
          )
          assert(
            copiedCampaign.isActive == true,
            "Copy of the campaign is active"
          )
          true
        }
      )
    }
  }

  test("list campaigns by continent") {
    check {
      forAll(
        (
            userCreate: User.Create,
            campaignCreates1: List[Campaign.Create],
            campaignCreates2: List[Campaign.Create],
            continent: Continent
        ) => {
          val pageSize = 30
          val pageRequest = PageRequest(0, pageSize, Map.empty)
          val listIO = for {
            user <- UserDao.create(userCreate)
            _ <- campaignCreates1
              .take(pageSize) traverse { toInsert =>
              CampaignDao
                .insertCampaign(
                  toInsert.copy(parentCampaignId = None, continent = None),
                  user
                )
            }
            insertedCampaigns2 <- campaignCreates2
              .take(pageSize) traverse { toInsert =>
              CampaignDao
                .insertCampaign(
                  toInsert.copy(
                    parentCampaignId = None,
                    continent = Some(continent)
                  ),
                  user
                )
            }
            listed <- CampaignDao
              .listCampaigns(
                pageRequest,
                CampaignQueryParameters(continent = Some(continent)),
                user
              )
          } yield (listed, insertedCampaigns2)

          val (listedCampaigns, dbCampaigns) = listIO.transact(xa).unsafeRunSync

          val expectedIds = (dbCampaigns.take(pageSize) map { _.id }).toSet

          assert(
            expectedIds == (listedCampaigns.results map { _.id }).toSet,
            "Listed campaigns are those expected from campaign insertion with specified continent"
          )

          true
        }
      )
    }
  }

  test("list only active campaigns ") {
    check {
      forAll(
        (
            userCreate: User.Create,
            campaignCreates1: List[Campaign.Create],
            campaignCreates2: List[Campaign.Create]
        ) => {
          val pageSize = 30
          val pageRequest = PageRequest(0, pageSize, Map.empty)
          val listIO = for {
            user <- UserDao.create(userCreate)
            insertedCampaigns1 <- campaignCreates1
              .take(pageSize) traverse { toInsert =>
              CampaignDao
                .insertCampaign(
                  toInsert.copy(parentCampaignId = None),
                  user
                )
            }
            _ <- insertedCampaigns1 traverse { campaign =>
              CampaignDao
                .updateCampaign(campaign.copy(isActive = false), campaign.id)
            }
            insertedCampaigns2 <- campaignCreates2
              .take(pageSize) traverse { toInsert =>
              CampaignDao
                .insertCampaign(
                  toInsert.copy(
                    parentCampaignId = None
                  ),
                  user
                )
            }
            listed <- CampaignDao
              .listCampaigns(
                pageRequest.copy(limit = pageSize * 2),
                CampaignQueryParameters(isActive = Some(true)),
                user
              )
          } yield (listed, insertedCampaigns2)

          val (listedCampaigns, dbCampaigns) = listIO.transact(xa).unsafeRunSync

          val expectedIds = (dbCampaigns.take(pageSize) map { _.id }).toSet

          assert(
            expectedIds == (listedCampaigns.results map { _.id }).toSet,
            "Listed campaigns are those expected from campaign insertion that are active"
          )

          true
        }
      )
    }
  }

  test("get users who have campaign copies") {
    check {
      forAll(
        (
            userCreates: List[User.Create],
            userCreate: User.Create,
            campaignCreate: Campaign.Create,
            annotationProjectCreate: AnnotationProject.Create
        ) => {
          val copyIO = for {
            parent <- UserDao.create(userCreate)
            children <- userCreates traverse { u =>
              UserDao.create(u)
            }
            insertedCampaign <- CampaignDao
              .insertCampaign(
                campaignCreate.copy(parentCampaignId = None),
                parent
              )
            insertedProject <- AnnotationProjectDao
              .insert(
                annotationProjectCreate.copy(
                  campaignId = Some(insertedCampaign.id),
                  status = AnnotationProjectStatus.Waiting
                ),
                parent
              )
            _ <- AnnotationProjectDao.update(
              insertedProject.toProject
                .copy(status = AnnotationProjectStatus.Ready),
              insertedProject.id
            )
            _ <- children traverse { child =>
              CampaignDao.copyCampaign(insertedCampaign.id, child)
            }
            cloneOwners <- CampaignDao.getCloneOwners(insertedCampaign.id)
          } yield cloneOwners

          val cloneOwners = copyIO.transact(xa).unsafeRunSync

          assert(
            cloneOwners.length == userCreates.length,
            "Returned number of clone owners matches the number of users created"
          )
          true
        }
      )
    }
  }
}
