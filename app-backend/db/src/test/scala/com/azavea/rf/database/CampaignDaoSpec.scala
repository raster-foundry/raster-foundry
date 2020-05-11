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

}
