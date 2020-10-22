package com.rasterfoundry.database

import com.rasterfoundry.common.Generators.Implicits._
import com.rasterfoundry.datamodel._
import com.rasterfoundry.datamodel.newtypes._

import doobie.implicits._
import org.scalacheck.Prop.forAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.Checkers

class AsyncCampaignCloneDaoSpec
    extends AnyFunSuite
    with Checkers
    with Matchers
    with DBTestConfig
    with PropTestHelpers {

  test("create an async bulk user create job") {
    check {
      forAll {
        (
            userCreate: User.Create,
            clone: Campaign.Clone
        ) =>
          {
            val dbIO = for {
              dbUser <- UserDao.create(userCreate)
              insertedAsyncJob <- AsyncCampaignCloneDao
                .insertAsyncCampaignClone(clone, dbUser)
            } yield (insertedAsyncJob)

            val inserted = dbIO.transact(xa).unsafeRunSync

            assert(
              inserted.status == AsyncJobStatus.Accepted,
              "On creation, a job's status should be accepted"
            )

            assert(
              inserted.input == clone,
              "Input parameters should be copied without error"
            )

            true
          }
      }
    }
  }

  test("fail an async bulk user create job") {
    check {
      forAll {
        (
            userCreate: User.Create,
            clone: Campaign.Clone
        ) =>
          {
            val dbIO = for {
              dbUser <- UserDao.create(userCreate)
              insertedAsyncJob <- AsyncCampaignCloneDao
                .insertAsyncCampaignClone(clone, dbUser)
              failed <- AsyncCampaignCloneDao.fail(
                insertedAsyncJob.id,
                new AsyncJobErrors(List("oh no", "bad stuff", "so much wrong"))
              )
            } yield (failed)

            val Some(failed) = dbIO.transact(xa).unsafeRunSync

            assert(
              failed.status == AsyncJobStatus.Failed,
              "After failing the job, its status should be FAILED"
            )

            assert(
              failed.errors.value ==
                List("oh no", "bad stuff", "so much wrong"),
              "Errors should be transcribed faithfully"
            )

            true
          }
      }
    }
  }

  test("mark an async bulk user create job successful") {
    check {
      forAll {
        (
            userCreate: User.Create,
            campaignCreate: Campaign.Create,
            clone: Campaign.Clone
        ) =>
          {
            val dbIO = for {
              dbUser <- UserDao.create(userCreate)
              dbCampaign <- CampaignDao.insertCampaign(
                campaignCreate.copy(parentCampaignId = None),
                dbUser
              )
              insertedAsyncJob <- AsyncCampaignCloneDao
                .insertAsyncCampaignClone(clone, dbUser)
              pretendClonedCampaign <- CampaignDao.insertCampaign(
                campaignCreate.copy(parentCampaignId = Some(dbCampaign.id)),
                dbUser
              )
              succeeded <- AsyncCampaignCloneDao.succeed(
                insertedAsyncJob.id,
                pretendClonedCampaign
              )
            } yield (succeeded, pretendClonedCampaign)

            val (Some(succeeded), campaignCopy) =
              dbIO.transact(xa).unsafeRunSync

            assert(
              succeeded.status == AsyncJobStatus.Succeeded,
              "After succeeding, its status should be SUCCEEDED"
            )

            assert(
              succeeded.results == campaignCopy,
              "Results should be transcribed faithfully"
            )

            true
          }
      }
    }
  }

}
