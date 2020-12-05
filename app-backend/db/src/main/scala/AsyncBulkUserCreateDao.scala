package com.rasterfoundry.database

import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.datamodel._
import com.rasterfoundry.datamodel.newtypes._

import cats.syntax.apply._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._

import java.util.UUID

object AsyncBulkUserCreateDao extends Dao[AsyncBulkUserCreate] {
  val tableName = "async_user_bulk_create"

  override val fieldNames = List(
    "id",
    "owner",
    "input",
    "status",
    "errors",
    "results"
  )

  val selectF = fr"select" ++ selectFieldsF ++ fr"from" ++ tableF

  def getAsyncBulkUserCreate(
      id: UUID
  ): ConnectionIO[Option[AsyncBulkUserCreate]] =
    query.filter(id).selectOption

  def insertAsyncBulkUserCreate(
      bulkCreate: UserBulkCreate,
      user: User
  ): ConnectionIO[AsyncBulkUserCreate] =
    (fr"INSERT INTO" ++ tableF ++ fr"""
    (id, owner, input, status) VALUES (
      uuid_generate_v4(), ${user.id}, $bulkCreate, 'ACCEPTED'
    )""").update.withUniqueGeneratedKeys[AsyncBulkUserCreate](
      fieldNames: _*
    )

  def succeed(
      id: UUID,
      results: CreatedUserIds
  ): ConnectionIO[Option[AsyncBulkUserCreate]] =
    (fr"update" ++ tableF ++ fr"""
          set status = 'SUCCEEDED', results = $results where id = $id
    """).update.run *> getAsyncBulkUserCreate(id)

  def fail(
      id: UUID,
      errors: AsyncJobErrors
  ): ConnectionIO[Option[AsyncBulkUserCreate]] =
    (fr"update" ++ tableF ++ fr"""
          set status = 'FAILED', errors = $errors where id = $id
    """).update.run *> getAsyncBulkUserCreate(id)

}
