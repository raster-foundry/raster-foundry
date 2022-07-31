package com.rasterfoundry.database

import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.datamodel.PageRequest
import com.rasterfoundry.datamodel._

import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import doobie.postgres.implicits._

import java.util.UUID

object HITLJobDao extends Dao[HITLJob] {
  val tableName = "hitl_jobs"

  val selectF: Fragment = sql"""
      SELECT
        id, created_at, created_by, modified_at, owner,
        campaign_id, project_id, status, version
      FROM
    """ ++ tableF

  override val fieldNames = List(
    "id",
    "created_at",
    "created_by",
    "modified_at",
    "owner",
    "campaign_id",
    "project_id",
    "status",
    "version"
  )

  def unsafeGetById(id: UUID): ConnectionIO[HITLJob] =
    query.filter(id).select

  def getById(id: UUID): ConnectionIO[Option[HITLJob]] =
    query.filter(id).selectOption

  def list(
      page: PageRequest,
      params: HITLJobQueryParameters,
      user: User
  ): ConnectionIO[PaginatedResponse[HITLJob]] =
    query
      .filter(params)
      .filter(user.isSuperuser && user.isActive match {
        case true  => fr"true"
        case false => fr"owner = ${user.id}"
      })
      .page(page)

  def getNewVersion(
      user: User,
      campaignId: UUID,
      projectId: UUID
  ): ConnectionIO[Int] =
    query
      .filter(fr"owner = ${user.id}")
      .filter(fr"campaign_id = ${campaignId}")
      .filter(fr"project_id = ${projectId}")
      .list
      .map { listed =>
        val versions = listed.map(_.version)
        versions.size match {
          case 0 => 0
          case _ => versions.max + 1
        }
      }

  def insertF(newHITLJob: HITLJob.Create, user: User, version: Int): Fragment =
    fr"INSERT INTO" ++ tableF ++ fr"""
      (id, created_at, created_by, modified_at, owner,
        campaign_id, project_id, status, version)
    VALUES
      (uuid_generate_v4(), now(), ${user.id}, now(), ${user.id},
      ${newHITLJob.campaignId}, ${newHITLJob.projectId}, ${newHITLJob.status}, ${version})
    """

  def create(
      newHITLJob: HITLJob.Create,
      user: User
  ): ConnectionIO[HITLJob] =
    for {
      version <- getNewVersion(user,
                               newHITLJob.campaignId,
                               newHITLJob.projectId)
      inserted <- insertF(newHITLJob, user, version).update
        .withUniqueGeneratedKeys[HITLJob](fieldNames: _*)
    } yield inserted

  def update(hitlJob: HITLJob, id: UUID): ConnectionIO[Int] = {
    (fr"UPDATE " ++ tableF ++ fr"""SET
      modified_at = now(),
      status = ${hitlJob.status}
    WHERE
      id = $id
    """).update.run;
  }

  def delete(id: UUID): ConnectionIO[Int] =
    query.filter(id).delete

  def isOwnerOrSuperUser(user: User, id: UUID): ConnectionIO[Boolean] =
    for {
      jobO <- getById(id)
      isSuperuser = user.isSuperuser && user.isActive
    } yield {
      jobO match {
        case Some(job) => job.owner == user.id || isSuperuser
        case _         => isSuperuser
      }
    }

  def hasInProgressJob(
      campaignId: UUID,
      projectId: UUID,
      user: User
  ): ConnectionIO[Boolean] =
    query
      .filter(fr"campaign_id = ${campaignId}")
      .filter(fr"project_id = ${projectId}")
      .filter(fr"owner = ${user.id}")
      .filter(
        fr"""
        status IN (
          'NOTRUN'::public.hitl_job_status,
          'TORUN'::public.hitl_job_status,
          'RUNNING'::public.hitl_job_status
        )
        """
      )
      .exists

  def hasSuccessfulJob(
      campaignId: UUID,
      projectId: UUID,
      user: User
  ): ConnectionIO[Boolean] =
    query
      .filter(fr"campaign_id = ${campaignId}")
      .filter(fr"project_id = ${projectId}")
      .filter(fr"owner = ${user.id}")
      .filter(
        fr"""
        status = 'RAN'::public.hitl_job_status
        """
      )
      .exists
}
