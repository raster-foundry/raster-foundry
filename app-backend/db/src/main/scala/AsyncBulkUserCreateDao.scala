package com.rasterfoundry.database

import com.rasterfoundry.database.Implicits._

import com.rasterfoundry.datamodel._

import cats.syntax.traverse._
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

  val selectF = fr"select" ++ selectFieldsF ++ fr"from $tableName"

  def getAsyncBulkUserCreate(
      id: UUID
  ): ConnectionIO[Option[AsyncBulkUserCreate]] =
    query.filter(id).selectOption

  def insertAsyncBulkUserCreate(
      bulkCreate: UserBulkCreate,
      user: User
  ): ConnectionIO[AsyncBulkUserCreate] =
    (Fragment.const(s"INSERT INTO $tableName") ++ fr"""
    (id, owner, input, status) VALUES (
      uuid_generate_v4(), ${user.id}, $bulkCreate, 'ACCEPTED'    
    )""").update.withUniqueGeneratedKeys[AsyncBulkUserCreate](
      "id",
      "owner",
      "input",
      "status",
      "errors",
      "results"
    )

  def succeed(
      id: UUID,
      results: List[UserWithCampaign]
  ): ConnectionIO[Option[AsyncBulkUserCreate]] =
    for {
      jobO <- getAsyncBulkUserCreate(id)
      _ <- jobO traverse { job =>
        (Fragment.const(s"update $tableName") ++ fr"""
          set status = 'SUCCEEDED', results = $results where id = ${job.id}
        """).update.run
      }
      out <- jobO flatTraverse { job =>
        getAsyncBulkUserCreate(job.id)
      }
    } yield out

  def fail(
      id: UUID,
      errors: List[String]
  ): ConnectionIO[Option[AsyncBulkUserCreate]] =
    for {
      jobO <- getAsyncBulkUserCreate(id)
      _ <- jobO traverse { job =>
        (Fragment.const(s"update $tableName") ++ fr"""
          set status = 'FAILED', errors = $errors where id = ${job.id}
        """).update.run
      }
      out <- jobO flatTraverse { job =>
        getAsyncBulkUserCreate(job.id)
      }

    } yield out

}
