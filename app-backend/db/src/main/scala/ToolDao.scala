package com.rasterfoundry.database

import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.common.datamodel.{
  Tool,
  User,
  ObjectType,
  GroupType,
  ActionType
}

import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.postgres.circe.jsonb.implicits._

import java.util.UUID
import java.sql.Timestamp

object ToolDao extends Dao[Tool] with ObjectPermissions[Tool] {
  val tableName = "tools"

  val selectF = sql"""
    SELECT
      id, created_at, modified_at, created_by, modified_by, owner, title,
      description, requirements, license, visibility, compatible_data_sources, stars, definition,
      single_source
    FROM """ ++ tableF

  def insert(newTool: Tool.Create, user: User): ConnectionIO[Tool] = {
    val id = UUID.randomUUID()
    val now = new Timestamp(new java.util.Date().getTime())
    val ownerId = util.Ownership.checkOwner(user, newTool.owner)

    sql"""
       INSERT INTO tools
         (id, created_at, modified_at, created_by, modified_by, owner, title,
          description, requirements, license, visibility, compatible_data_sources, stars, definition,
          single_source)
       VALUES
         (${id}, ${now}, ${now}, ${user.id}, ${user.id}, ${ownerId}, ${newTool.title},
          ${newTool.description}, ${newTool.requirements}, ${newTool.license}, ${newTool.visibility},
          ${newTool.compatibleDataSources}, ${newTool.stars}, ${newTool.definition}, ${newTool.singleSource})
       """.update.withUniqueGeneratedKeys[Tool](
      "id",
      "created_at",
      "modified_at",
      "created_by",
      "modified_by",
      "owner",
      "title",
      "description",
      "requirements",
      "license",
      "visibility",
      "compatible_data_sources",
      "stars",
      "definition",
      "single_source"
    )
  }

  def update(tool: Tool, id: UUID, user: User): ConnectionIO[Int] = {
    val updateTime = new Timestamp(new java.util.Date().getTime())
    val idFilter = fr"id = ${id}"
    (sql"""
       UPDATE tools
       SET
         modified_by = ${user.id},
         modified_at = ${updateTime},
         title = ${tool.title},
         description = ${tool.description},
         requirements = ${tool.requirements},
         license = ${tool.license},
         visibility = ${tool.visibility},
         compatible_data_sources = ${tool.compatibleDataSources},
         stars = ${tool.stars},
         definition = ${tool.definition},
         single_source = ${tool.singleSource}
     """ ++ Fragments.whereAndOpt(Some(idFilter))).update.run
  }

  def authQuery(user: User,
                objectType: ObjectType,
                ownershipTypeO: Option[String] = None,
                groupTypeO: Option[GroupType] = None,
                groupIdO: Option[UUID] = None): Dao.QueryBuilder[Tool] =
    user.isSuperuser match {
      case true =>
        Dao.QueryBuilder[Tool](selectF, tableF, List.empty)
      case false =>
        Dao.QueryBuilder[Tool](selectF,
                               tableF,
                               List(
                                 queryObjectsF(user,
                                               objectType,
                                               ActionType.View,
                                               ownershipTypeO,
                                               groupTypeO,
                                               groupIdO)))
    }

  def authorized(user: User,
                 objectType: ObjectType,
                 objectId: UUID,
                 actionType: ActionType): ConnectionIO[Boolean] =
    this.query
      .filter(authorizedF(user, objectType, actionType))
      .filter(objectId)
      .exists
}
