package com.rasterfoundry.database

import com.rasterfoundry.common.Generators.Implicits._
import com.rasterfoundry.datamodel._

import doobie.implicits._
import org.scalacheck.Prop.forAll

import org.scalatestplus.scalacheck.Checkers
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class HITLJobDaoSpec
    extends AnyFunSuite
    with Matchers
    with Checkers
    with DBTestConfig
    with PropTestHelpers {

  test("creating HITL jobs") {
    check {
      forAll(
        (
            userCreate: User.Create,
            projectCreate: AnnotationProject.Create,
            campaignCreate: Campaign.Create,
            hitlJobCreate: HITLJob.Create
        ) => {
          val createHITLJobIO =
            for {
              dbUser <- UserDao.create(userCreate)
              dbCampaign <-
                CampaignDao
                  .insertCampaign(
                    campaignCreate.copy(parentCampaignId = None),
                    dbUser
                  )
              dbCampaignAlt <-
                CampaignDao
                  .insertCampaign(
                    campaignCreate.copy(parentCampaignId = None),
                    dbUser
                  )
              dbProject <-
                AnnotationProjectDao
                  .insert(
                    projectCreate.copy(campaignId = Option(dbCampaign.id)),
                    dbUser
                  )
              dbProjectAlt <-
                AnnotationProjectDao
                  .insert(
                    projectCreate.copy(campaignId = Option(dbCampaignAlt.id)),
                    dbUser
                  )
              dbHITLJobOne <- HITLJobDao.create(
                hitlJobCreate
                  .copy(campaignId = dbCampaign.id, projectId = dbProject.id),
                dbUser
              )
              dbHITLJobTwo <- HITLJobDao.create(
                hitlJobCreate
                  .copy(campaignId = dbCampaign.id, projectId = dbProject.id),
                dbUser
              )
              dbHITLJobThree <- HITLJobDao.create(
                hitlJobCreate.copy(
                  campaignId = dbCampaignAlt.id,
                  projectId = dbProjectAlt.id
                ),
                dbUser
              )
            } yield (
              dbUser,
              dbCampaign,
              dbProject,
              dbHITLJobOne,
              dbHITLJobTwo,
              dbHITLJobThree
            )
          val (user, campaign, project, jobOne, jobTwo, jobThree) =
            createHITLJobIO.transact(xa).unsafeRunSync

          assert(
            user.id == jobOne.owner,
            "Inserted HITL job owner should be the same as user"
          )
          assert(
            campaign.id == jobOne.campaignId,
            "Inserted HITL job campaign ID should be the same as the inserted campaign"
          )
          assert(
            project.id == jobOne.projectId,
            "Inserted HITL job project ID should be the same as the inserted project"
          )
          assert(
            hitlJobCreate.status == jobOne.status,
            "Inserted HITL job status should be the same as sent"
          )
          assert(
            jobOne.version < jobTwo.version,
            "HITL jobs should have increasing version numbers for same user + campaign + project"
          )
          assert(
            jobTwo.version != jobThree.version,
            "HITL jobs across campaign and project has no version relation"
          )
          true
        }
      )
    }
  }

  test("getting a HITL Job record by id") {
    check {
      forAll(
        (
            userCreate: User.Create,
            projectCreate: AnnotationProject.Create,
            campaignCreate: Campaign.Create,
            hitlJobCreate: HITLJob.Create
        ) => {
          val selectHITLJobIO = for {
            dbUser <- UserDao.create(userCreate)
            dbCampaign <-
              CampaignDao
                .insertCampaign(
                  campaignCreate.copy(parentCampaignId = None),
                  dbUser
                )
            dbProject <-
              AnnotationProjectDao
                .insert(
                  projectCreate.copy(campaignId = Option(dbCampaign.id)),
                  dbUser
                )
            dbHITLJob <- HITLJobDao.create(
              hitlJobCreate
                .copy(campaignId = dbCampaign.id, projectId = dbProject.id),
              dbUser
            )
            selectedJobOption <- HITLJobDao.getById(dbHITLJob.id)
          } yield (dbUser, dbHITLJob, selectedJobOption)

          val (user, job, selectedJobO) =
            selectHITLJobIO.transact(xa).unsafeRunSync

          selectedJobO match {
            case Some(selectedJob) =>
              assert(
                job.id == selectedJob.id,
                "Inserted and selected job IDs should be the same"
              )
              assert(
                user.id == selectedJob.owner,
                "Selected job owner should be the same as user"
              )
              assert(
                job.campaignId == selectedJob.campaignId,
                "Inserted and selected job camapgin IDs should be the same"
              )
              assert(
                job.projectId == selectedJob.projectId,
                "Inserted and selected job project IDs should be the same"
              )
              assert(
                job.status == selectedJob.status,
                "Inserted and selected job statuses should be the same"
              )
              assert(
                job.version == selectedJob.version,
                "Inserted and selected job versions should be the same"
              )
              true
            case _ => false
          }
        }
      )
    }
  }

  test("updating a HITL job") {
    check {
      forAll(
        (
            userCreate: User.Create,
            projectCreate: AnnotationProject.Create,
            campaignCreate: Campaign.Create,
            hitlJobCreate: HITLJob.Create
        ) => {
          val updateHITLJobIO = for {
            dbUser <- UserDao.create(userCreate)
            dbCampaign <-
              CampaignDao
                .insertCampaign(
                  campaignCreate.copy(parentCampaignId = None),
                  dbUser
                )
            dbProject <-
              AnnotationProjectDao
                .insert(
                  projectCreate.copy(campaignId = Option(dbCampaign.id)),
                  dbUser
                )
            dbHITLJob <- HITLJobDao.create(
              hitlJobCreate
                .copy(campaignId = dbCampaign.id, projectId = dbProject.id),
              dbUser
            )
            _ <- HITLJobDao.update(
              dbHITLJob.copy(status = HITLJobStatus.Running),
              dbHITLJob.id
            )
            updatedJobOption <- HITLJobDao.getById(dbHITLJob.id)
          } yield (dbUser, dbHITLJob, updatedJobOption)

          val (user, job, updatedJobO) =
            updateHITLJobIO.transact(xa).unsafeRunSync

          updatedJobO match {
            case Some(updatedJob) =>
              assert(
                job.id == updatedJob.id,
                "Inserted and updated job IDs should be the same"
              )
              assert(
                user.id == updatedJob.owner,
                "Updated job owner should be the same as user"
              )
              assert(
                job.campaignId == updatedJob.campaignId,
                "Inserted and Updated job camapgin IDs should be the same"
              )
              assert(
                job.projectId == updatedJob.projectId,
                "Inserted and Updated job project IDs should be the same"
              )
              assert(
                job.status != updatedJob.status,
                "Inserted and Updated job statuses should not be the same"
              )
              assert(
                updatedJob.status == HITLJobStatus.Running,
                "Job status should be updated"
              )
              assert(
                job.version == updatedJob.version,
                "Inserted and updated job versions should be the same"
              )
              true
            case _ => false
          }
        }
      )
    }
  }

  test("deleting a HITL job") {
    check {
      forAll(
        (
            userCreate: User.Create,
            projectCreate: AnnotationProject.Create,
            campaignCreate: Campaign.Create,
            hitlJobCreate: HITLJob.Create
        ) => {
          val deleteHITLJobIO = for {
            dbUser <- UserDao.create(userCreate)
            dbCampaign <-
              CampaignDao
                .insertCampaign(
                  campaignCreate.copy(parentCampaignId = None),
                  dbUser
                )
            dbProject <-
              AnnotationProjectDao
                .insert(
                  projectCreate.copy(campaignId = Option(dbCampaign.id)),
                  dbUser
                )
            dbHITLJob <- HITLJobDao.create(
              hitlJobCreate
                .copy(campaignId = dbCampaign.id, projectId = dbProject.id),
              dbUser
            )
            deletedRowCount <- HITLJobDao.delete(dbHITLJob.id)
            selectedJobOption <- HITLJobDao.getById(dbHITLJob.id)
          } yield { (deletedRowCount, selectedJobOption) }

          val (rowCount, selectedJobO) =
            deleteHITLJobIO.transact(xa).unsafeRunSync

          assert(
            rowCount == 1,
            "Should have one record deleted"
          )
          assert(
            selectedJobO == None,
            "Inserted HITL job should be deleted from DB"
          )
          true
        }
      )
    }
  }

  test("listing HITL jobs") {
    check {
      forAll(
        (
            userCreate: User.Create,
            projectCreate: AnnotationProject.Create,
            campaignCreate: Campaign.Create,
            hitlJobCreate: HITLJob.Create,
            page: PageRequest
        ) => {
          val listHITLJobsIO =
            for {
              dbUser <- UserDao.create(userCreate)
              dbCampaign <-
                CampaignDao
                  .insertCampaign(
                    campaignCreate.copy(parentCampaignId = None),
                    dbUser
                  )
              dbCampaignAlt <-
                CampaignDao
                  .insertCampaign(
                    campaignCreate.copy(parentCampaignId = None),
                    dbUser
                  )
              dbProject <-
                AnnotationProjectDao
                  .insert(
                    projectCreate.copy(campaignId = Option(dbCampaign.id)),
                    dbUser
                  )
              dbProjectAlt <-
                AnnotationProjectDao
                  .insert(
                    projectCreate.copy(campaignId = Option(dbCampaignAlt.id)),
                    dbUser
                  )
              dbHITLJobOne <- HITLJobDao.create(
                hitlJobCreate
                  .copy(campaignId = dbCampaign.id, projectId = dbProject.id),
                dbUser
              )
              dbHITLJobTwo <- HITLJobDao.create(
                hitlJobCreate
                  .copy(campaignId = dbCampaign.id, projectId = dbProject.id),
                dbUser
              )
              dbHITLJobThree <- HITLJobDao.create(
                hitlJobCreate.copy(
                  campaignId = dbCampaignAlt.id,
                  projectId = dbProjectAlt.id
                ),
                dbUser
              )
              jobsBatchOne <- HITLJobDao.list(
                page,
                HITLJobQueryParameters(
                  campaignId = Some(dbCampaign.id),
                  projectId = Some(dbProject.id)
                ),
                dbUser
              )
              jobsBatchTwo <- HITLJobDao.list(
                page,
                HITLJobQueryParameters(
                  campaignId = Some(dbCampaignAlt.id),
                  projectId = Some(dbProjectAlt.id)
                ),
                dbUser
              )
              jobsBatchThree <- HITLJobDao.list(
                page,
                HITLJobQueryParameters(
                  campaignId = Some(dbCampaign.id),
                  projectId = Some(dbProjectAlt.id)
                ),
                dbUser
              )
            } yield (
              dbHITLJobOne,
              dbHITLJobTwo,
              dbHITLJobThree,
              jobsBatchOne,
              jobsBatchTwo,
              jobsBatchThree
            )

          val (jobOne, jobTwo, jobThree, batchOne, batchTwo, batchThree) =
            listHITLJobsIO.transact(xa).unsafeRunSync

          assert(
            batchOne.count == 2,
            "Should have two records matching the job list filters combo 1"
          )
          assert(
            batchTwo.count == 1,
            "Should have one record matching the job list filters combo 2"
          )
          assert(
            batchThree.count == 0,
            "Should have no record matching the job list filters combo 3"
          )
          assert(
            batchOne.results.map(_.id).toSet == Set(jobOne.id, jobTwo.id),
            "Listing with param combo 1 should return job 1 and 2"
          )
          assert(
            batchTwo.results.map(_.id).toSet == Set(jobThree.id),
            "Listing with param combo 2 should return job 3"
          )
          assert(
            batchThree.results.map(_.id).size == 0,
            "Listing with param combo 3 should have empty result"
          )
          true
        }
      )
    }
  }
}
