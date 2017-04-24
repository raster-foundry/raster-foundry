package com.azavea.rf.database.tables

import java.sql.Timestamp

import com.azavea.rf.database.query._
import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.database.fields.{TimestampFields, UserFkFields, ToolCategoryFields}
import com.azavea.rf.database.{Database => DB}
import com.azavea.rf.database.sort._
import com.azavea.rf.datamodel._

import com.typesafe.scalalogging.LazyLogging
import slick.model.ForeignKeyAction

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import com.lonelyplanet.akka.http.extensions.PageRequest

/** Table that represents categories for tools in the Raster Foundry lab
  *
  * These are user generated categories for tools to help users
  * track and search for them. For instance, users may decide to
  * create categories for coursework, projects, etc.
  */
class ToolCategories(_tableTag: Tag)
  extends Table[ToolCategory](_tableTag, "tool_categories")
    with ToolCategoryFields
    with LazyLogging
    with TimestampFields {

  def * =
    (slugLabel, createdAt, modifiedAt, createdBy, modifiedBy, category) <> (ToolCategory.tupled, ToolCategory.unapply)

  val slugLabel: Rep[String] = column[String]("slug_label", O.PrimaryKey)
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
  type TableQuery = Query[ToolCategories, ToolCategory, Seq]


  implicit val toolCategoriesSorter: QuerySorter[ToolCategories] =
    new QuerySorter(
      new ToolCategoryFieldsSort(identity[ToolCategories]),
      new TimestampSort(identity[ToolCategories]))

  implicit class withToolCategoriesTableQuery[M, U, C[_]](toolCategories: TableQuery) {
    def page(combinedParams: CombinedToolCategoryQueryParams, pageRequest: PageRequest):
        ToolCategories.TableQuery = {
      val sorted = toolCategories.sort(pageRequest.sort)
      sorted.drop(pageRequest.offset * pageRequest.limit).take(pageRequest.limit)
    }
    def filterByToolCategoryParams(toolCategoryParams: ToolCategoryQueryParameters): ToolCategories.TableQuery = {
      toolCategories.filter(
        toolCategory => {
          val toolCategoryFilterConditions = List(
            toolCategoryParams.search.map(search => toolCategory.category.toLowerCase.like(s"%${search.toLowerCase()}%"))
          )
          toolCategoryFilterConditions
            .collect({case Some(criteria) => criteria})
            .reduceLeftOption(_ && _)
            .getOrElse(true: Rep[Boolean])
        }
      )
    }
  }

  def listToolCategories(pageRequest: PageRequest, combinedParams: CombinedToolCategoryQueryParams)
                        (implicit database: DB): Future[PaginatedResponse[ToolCategory]] = {
    val toolCategoriesQuery = ToolCategories
      .filterByToolCategoryParams(combinedParams.toolCategoryParams)
      .filterByTimestamp(combinedParams.timestampParams)

    val toolCategoriesQueryResult = database.db.run {
      val action = toolCategoriesQuery
        .page(combinedParams, pageRequest)
        .result
      logger.debug(s"Paginated Query for Tool Categories -- SQL: ${action.statements.headOption}")
      action
    }

    val totalToolCategoriesQueryResult = database.db.run {
      val action = toolCategoriesQuery
        .length
        .result
      logger.debug(s"Total Query for Tool Categories -- SQL: ${action.statements.headOption}")
      action
    }

    for {
      totalToolCategories <- totalToolCategoriesQueryResult
      toolCategories <- toolCategoriesQueryResult
    } yield {
      val hasNext = (pageRequest.offset + 1) * pageRequest.limit < totalToolCategories // 0 indexed page offset
      val hasPrevious = pageRequest.offset > 0
      PaginatedResponse(
        totalToolCategories, hasPrevious, hasNext, pageRequest.offset, pageRequest.limit, toolCategories.toSeq
      )
    }
  }

  /** Insert a tol category given a create case class with a user
    *
    * @param toolCategoryToCreate ToolCategory.Create object to use to create full tool category
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
  def getToolCategory(toolCategorySlug: String)(implicit database: DB): Future[Option[ToolCategory]] = {
    val fetchAction = ToolCategories.filter(_.slugLabel === toolCategorySlug).result.headOption

    database.db.run {
      fetchAction
    }
  }

  def deleteToolCategory(toolCategorySlug: String)(implicit database: DB): Future[Int] = {
    val deleteAction = ToolCategories.filter(_.slugLabel === toolCategorySlug)
    val toolCategoryQuery = for {
      c <- ToolCategories if c.slugLabel === toolCategorySlug
    } yield c

    database.db.run {
      toolCategoryQuery.delete
    }
  }
}
