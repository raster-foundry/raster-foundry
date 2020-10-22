package com.rasterfoundry.database

import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.datamodel._
import com.rasterfoundry.datamodel.newtypes._

import cats.syntax.apply._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._

import java.util.UUID

object AsyncCampaignCloneDao extends Dao[AsyncCampaignClone] {

  val tableName = "async_campaign_clone"

  override val fieldNames = List(
    "id",
    "owner",
    "input",
    "status",
    "errors",
    "results"
  )

  def selectF = fr"select" ++ selectFieldsF ++ fr"from" ++ tableF

  def getAsyncCampaignClone(
      id: UUID
  ): ConnectionIO[Option[AsyncCampaignClone]] =
    query.filter(id).selectOption

  def insertAsyncCampaignClone(
      campaignClone: Campaign.Clone,
      user: User
  ): ConnectionIO[AsyncCampaignClone] =
    (fr"INSERT INTO" ++ tableF ++ fr"""
  (id, owner, input, status) VALUES (
      uuid_generate_v4(), ${user.id}, $campaignClone, 'ACCEPTED'
  )""").update.withUniqueGeneratedKeys[AsyncCampaignClone](
      "id",
      "owner",
      "input",
      "status",
      "errors",
      "results"
    )

  def succeed(
      id: UUID,
      results: Campaign
  ): ConnectionIO[Option[AsyncCampaignClone]] =
    (fr"update" ++ tableF ++ fr"""
          set status = 'SUCCEEDED', results = $results where id = $id
    """).update.run *> getAsyncCampaignClone(id)

  def fail(
      id: UUID,
      errors: AsyncJobErrors
  ): ConnectionIO[Option[AsyncCampaignClone]] =
    (fr"update" ++ tableF ++ fr"""
          set status = 'FAILED', errors = $errors where id = $id
    """).update.run *> getAsyncCampaignClone(id)
}
