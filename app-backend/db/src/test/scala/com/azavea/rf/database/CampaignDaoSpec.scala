package com.rasterfoundry.database

import com.rasterfoundry.common.Generators.Implicits._
import com.rasterfoundry.datamodel._

import doobie.implicits._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatestplus.scalacheck.Checkers

class CampaignDaoSpec
    extends FunSuite
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
              .insertCampaign(campaignCreate, user)
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
}
