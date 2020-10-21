package com.rasterfoundry.database

import com.rasterfoundry.common.Generators.Implicits._
import com.rasterfoundry.datamodel._

import doobie.implicits._
import org.scalacheck.Prop.forAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.Checkers

class AsyncBulkUserCreateDaoSpec
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
            organizationCreate: Organization.Create,
            platformCreate: Platform,
            bulkCreate: UserBulkCreate
        ) =>
          {
            val dbIO = for {
              (dbUser, dbOrg, dbPlatform) <- insertUserOrgPlatform(
                userCreate,
                organizationCreate,
                platformCreate
              )
              fixedUp = bulkCreate.copy(
                platformId = dbPlatform.id,
                organizationId = dbOrg.id,
                campaignId = None
              )
              insertedAsyncJob <- AsyncBulkUserCreateDao
                .insertAsyncBulkUserCreate(fixedUp, dbUser)
            } yield (fixedUp, insertedAsyncJob)

            val (fixedUp, inserted) = dbIO.transact(xa).unsafeRunSync

            assert(
              inserted.status == AsyncJobStatus.Accepted,
              "On creation, a job's status should be accepted"
            )

            assert(
              inserted.input == fixedUp,
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
            organizationCreate: Organization.Create,
            platformCreate: Platform,
            bulkCreate: UserBulkCreate
        ) =>
          {
            val dbIO = for {
              (dbUser, dbOrg, dbPlatform) <- insertUserOrgPlatform(
                userCreate,
                organizationCreate,
                platformCreate
              )
              fixedUp = bulkCreate.copy(
                platformId = dbPlatform.id,
                organizationId = dbOrg.id
              )
              insertedAsyncJob <- AsyncBulkUserCreateDao
                .insertAsyncBulkUserCreate(fixedUp, dbUser)
              failed <- AsyncBulkUserCreateDao.fail(
                insertedAsyncJob.id,
                List("oh no", "bad stuff", "so much wrong")
              )
            } yield (failed)

            val Some(failed) = dbIO.transact(xa).unsafeRunSync

            assert(
              failed.status == AsyncJobStatus.Failed,
              "After failing the job, its status should be FAILED"
            )

            assert(
              failed.errors == AsyncJobErrors(
                List("oh no", "bad stuff", "so much wrong")
              ),
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
            organizationCreate: Organization.Create,
            platformCreate: Platform,
            bulkCreate: UserBulkCreate,
            extraUserCreate: User.Create
        ) =>
          {
            val dbIO = for {
              (dbUser, dbOrg, dbPlatform) <- insertUserOrgPlatform(
                userCreate,
                organizationCreate,
                platformCreate
              )
              fixedUp = bulkCreate.copy(
                platformId = dbPlatform.id,
                organizationId = dbOrg.id
              )
              insertedAsyncJob <- AsyncBulkUserCreateDao
                .insertAsyncBulkUserCreate(fixedUp, dbUser)
              extraUser <- UserDao.create(extraUserCreate)
              success <- AsyncBulkUserCreateDao.succeed(
                insertedAsyncJob.id,
                List(UserWithCampaign(extraUser, None))
              )
            } yield (success, extraUser)

            val (Some(succeeded), extraUser) = dbIO.transact(xa).unsafeRunSync

            assert(
              succeeded.status == AsyncJobStatus.Succeeded,
              "After succeeding, its status should be SUCCEEDED"
            )

            assert(
              succeeded.results == List(extraUser),
              "Created users should be transcribed successfully"
            )

            true
          }
      }
    }
  }
}
