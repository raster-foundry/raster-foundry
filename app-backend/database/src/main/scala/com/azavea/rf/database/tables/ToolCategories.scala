package com.azavea.rf.database.tables

import java.sql.Timestamp
import java.util.UUID

import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.database.fields.{OrganizationFkFields, TimestampFields, UserFkFields}
import com.azavea.rf.database.{Database => DB}
import com.azavea.rf.datamodel._
import com.typesafe.scalalogging.LazyLogging
import slick.model.ForeignKeyAction

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/** Table that represents categories for tools in model lab
  *
  * These are user generated categories for tools to help users
  * track and search for them. For instance, users may decide to
  * create categories for coursework, projects, etc.
  */
class ToolCategories(_tableTag: Tag)
  extends Table[ToolCategory](_tableTag, "tool_categories")
    with LazyLogging
    with UserFkFields
    with TimestampFields {

  def * =
    (id, createdAt, modifiedAt, createdBy, modifiedBy, category) <> (ToolCategory.tupled, ToolCategory.unapply)

  val id: Rep[UUID] = column[UUID]("id", O.PrimaryKey)
  val createdAt: Rep[Timestamp] = column[Timestamp]("created_at")
  val createdBy: Rep[String] = column[String]("created_by", O.Length(255, varying = true))
  val modifiedAt: Rep[Timestamp] = column[Timestamp]("modified_at")
  val modifiedBy: Rep[String] = column[String]("modified_by", O.Length(255, varying = true))
  val category: Rep[String] = column[String]("category")

  lazy val createdByUserFK = foreignKey("tool_categories_created_by_fkey", createdBy, Users)(
    r => r.id,
    onUpdate = ForeignKeyAction.NoAction,
    onDelete = ForeignKeyAction.NoAction)
  lazy val modifiedByUserFK = foreignKey("tool_categories_modified_by_fkey", modifiedBy, Users)(
    r => r.id,
    onUpdate = ForeignKeyAction.NoAction,
    onDelete = ForeignKeyAction.NoAction)

}

object ToolCategories extends TableQuery(tag => new ToolCategories(tag)) with LazyLogging {
  type TableQuery = Query[ToolCategories, ToolCategories#TableElementType, Seq]

  /** Insert a tol category given a create case class with a user
    *
    * @param toolCategoryToCreate ToolCategory.Create object to use to create full tool tag
    * @param userId           String user/owner to create a new tool category with
    */
  def insertToolCategory(toolCategoryToCreate: ToolCategory.Create, userId: String)(
    implicit database: DB): Future[ToolCategory] = {
    val toolCategory = toolCategoryToCreate.toToolCategory(userId)
    val insertAction = ToolCategories.forceInsert(toolCategory)

    logger.debug(s"Inserting Tool Category -- SQL: ${insertAction.statements.headOption}")

    database.db.run {
      insertAction
    } map { _ =>
      toolCategory
    }
  }

  /** Given a tool category ID, attempt to retrieve it from the database
    *
    * @param toolCategoryId UUID ID of tool category to get from database
    */
  def getToolCategory(toolCategoryId: UUID)(implicit database: DB): Future[Option[ToolCategory]] = {
    val fetchAction = ToolCategories.filter(_.id === toolCategoryId).result.headOption

    database.db.run {
      fetchAction
    }
  }
}
