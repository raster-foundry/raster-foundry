package com.azavea.rf.database.tables

import java.sql.Timestamp
import java.util.UUID

import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.database.fields.{OrganizationFkFields, TimestampFields, UserFkFields}
import com.azavea.rf.database.{Database => DB}
import com.azavea.rf.datamodel._
import com.lonelyplanet.akka.http.extensions.PageRequest
import com.typesafe.scalalogging.LazyLogging
import slick.model.ForeignKeyAction

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/** Table that represents tags for tools in tool lab
  *
  * These are user generated tags for tools to help users
  * track and search for them. For instance, users may decide to
  * create tags for coursework, projects, etc.
  */
class ToolTags(_tableTag: Tag)
    extends Table[ToolTag](_tableTag, "tool_tags")
    with LazyLogging
    with OrganizationFkFields
    with UserFkFields
    with TimestampFields {

  def * =
    (id, createdAt, modifiedAt, organizationId, createdBy, modifiedBy, owner, tag) <> (ToolTag.tupled, ToolTag.unapply)

  val id: Rep[UUID] = column[UUID]("id", O.PrimaryKey)
  val createdAt: Rep[Timestamp] = column[Timestamp]("created_at")
  val createdBy: Rep[String] = column[String]("created_by", O.Length(255, varying = true))
  val modifiedAt: Rep[Timestamp] = column[Timestamp]("modified_at")
  val modifiedBy: Rep[String] = column[String]("modified_by", O.Length(255, varying = true))
  val owner: Rep[String] = column[String]("owner", O.Length(255,varying=true))
  val organizationId: Rep[UUID] = column[UUID]("organization_id")
  val tag: Rep[String] = column[String]("tag")

  lazy val ownerUserFK = foreignKey("tool_tags_owner_fkey", owner, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  lazy val organizationsFk =
    foreignKey("tool_tags_organization_id_fkey", organizationId, Organizations)(
      r => r.id,
      onUpdate = ForeignKeyAction.NoAction,
      onDelete = ForeignKeyAction.NoAction)
  lazy val createdByUserFK = foreignKey("tool_tags_created_by_fkey", createdBy, Users)(
    r => r.id,
    onUpdate = ForeignKeyAction.NoAction,
    onDelete = ForeignKeyAction.NoAction)
  lazy val modifiedByUserFK = foreignKey("tool_tags_modified_by_fkey", modifiedBy, Users)(
    r => r.id,
    onUpdate = ForeignKeyAction.NoAction,
    onDelete = ForeignKeyAction.NoAction)

}

object ToolTags extends TableQuery(tag => new ToolTags(tag)) with LazyLogging {

  type TableQuery = Query[ToolTags, ToolTags#TableElementType, Seq]

  implicit class withToolTagsJoinQuery[M, U, C[_]](toolTags: ToolTags.TableQuery)
      extends ToolTagsTableQuery[M, U, C](toolTags)

  /** List tool tags given a page request
    *
    * @param pageRequest PageRequest information about sorting and page size
    */
  def listToolTags(pageRequest: PageRequest, user: User)
                   (implicit database: DB): Future[PaginatedResponse[ToolTag]] = {
    val toolTagQueryResult = database.db.run {
      val action = ToolTags.page(pageRequest, user).result
      logger.debug(s"Paginated Query for tool tags -- SQL: ${action.statements.headOption}")
      action
    }
    val totalToolTagsQueryResult = database.db.run {
      val action = ToolTags.filterToSharedOrganizationIfNotInRoot(user).length.result
      logger.debug(s"Total Query for tool tags -- SQL: ${action.statements.headOption}")
      action
    }

    for {
      totalTags <- totalToolTagsQueryResult
      tags <- toolTagQueryResult
    } yield {
      val hasNext = (pageRequest.offset + 1) * pageRequest.limit < totalTags // 0 indexed page offset
      val hasPrevious = pageRequest.offset > 0
      PaginatedResponse(totalTags,
        hasPrevious,
        hasNext,
        pageRequest.offset,
        pageRequest.limit,
        tags)
    }
  }

  /** Insert a tool tag given a create case class with a user
    *
    * @param toolTagtoCreate ToolTag.Create object to use to create full tool tag
    * @param user           User user/owner to create a new tool tag with
    */
  def insertToolTag(toolTagtoCreate: ToolTag.Create, user: User)(
      implicit database: DB): Future[ToolTag] = {
    val toolTag = toolTagtoCreate.toToolTag(user)
    val insertAction = ToolTags.forceInsert(toolTag)

    logger.debug(s"Inserting Tool Tag -- SQL: ${insertAction.statements.headOption}")

    database.db.run {
      insertAction
    } map { _ =>
      toolTag
    }
  }

  /** Given a tool tag ID, attempt to retrieve it from the database
    *
    * @param toolTagId UUID ID of tool tag to get from database
    * @param user      Results will be limited to user's organization
    */
  def getToolTag(toolTagId: UUID, user: User)(implicit database: DB): Future[Option[ToolTag]] = {
    val fetchAction = ToolTags
                        .filterToSharedOrganizationIfNotInRoot(user)
                        .filter(_.id === toolTagId)
                        .result
                        .headOption

    database.db.run {
      fetchAction
    }
  }

  /** Delete a given tool tag
    *
    * @param toolTagId UUID ID of tool tag to delete
    * @param user      Results will be limited to user's organization
    */
  def deleteToolTag(toolTagId: UUID, user: User)(implicit database: DB): Future[Int] = {
    database.db.run {
      ToolTags
        .filterToSharedOrganizationIfNotInRoot(user)
        .filter(_.id === toolTagId)
        .delete
    }
  }

  /** Update a tool tag's tag
    *
    * Note: Only updates the tag, while automatically setting the modification
    * time and who modified the tag
    *
    * @param toolTag ToolTag tool tag to use for update
    * @param toolTagId UUID ID of tag to perform update with
    * @param user User user performing update
    */
  def updateToolTag(toolTag: ToolTag, toolTagId: UUID, user: User)(
      implicit database: DB): Future[Int] = {
    val updateTime = new Timestamp((new java.util.Date).getTime)

    val updateToolTagQuery = for {
      updateToolTag <- ToolTags
                         .filterToSharedOrganizationIfNotInRoot(user)
                         .filter(_.id === toolTagId)
    } yield (updateToolTag.modifiedAt, updateToolTag.modifiedBy, updateToolTag.tag)

    database.db.run {
      updateToolTagQuery.update((updateTime, user.id, toolTag.tag))
    } map {
      case 1 => 1
      case _ => throw new IllegalStateException("Error while updating tool tag")
    }
  }
}

class ToolTagsTableQuery[M, U, C[_]](toolTags: ToolTags.TableQuery) {
  def page(pageRequest: PageRequest, user: User): ToolTags.TableQuery = {
    ToolTags
      .filterToSharedOrganizationIfNotInRoot(user)
      .drop(pageRequest.offset * pageRequest.limit)
      .take(pageRequest.limit)
  }
}
