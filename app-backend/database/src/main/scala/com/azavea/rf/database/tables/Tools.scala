package com.azavea.rf.database.tables

import io.circe._
import com.azavea.rf.database.fields._
import com.azavea.rf.database.sort._
import com.azavea.rf.datamodel._
import com.azavea.rf.database.query._
import com.azavea.rf.database.{Database => DB}
import com.azavea.rf.database.ExtendedPostgresDriver.api._

import slick.model.ForeignKeyAction
import com.lonelyplanet.akka.http.extensions.{PageRequest, Order}
import com.typesafe.scalalogging.LazyLogging

import java.util.UUID
import java.util.Date
import java.sql.Timestamp
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/** Table that represents tools in the Raster Foundry lab
  *
  * These are user generated tools that will eventually
  * contain a set of operations to be applied.
  */
class Tools(_tableTag: Tag) extends Table[Tool](_tableTag, "tools")
    with ToolFields
    with LazyLogging
    with OrganizationFkFields
    with UserFkVisibleFields
    with TimestampFields
{
  def * = (id, createdAt, modifiedAt, createdBy, modifiedBy, owner, organizationId,
    title, description, requirements, license, visibility, compatibleDataSources,
    stars, definition) <> (Tool.tupled, Tool.unapply)

  val id: Rep[UUID] = column[UUID]("id", O.PrimaryKey)
  val createdAt: Rep[Timestamp] = column[Timestamp]("created_at")
  val modifiedAt: Rep[Timestamp] = column[Timestamp]("modified_at")
  val createdBy: Rep[String] = column[String]("created_by", O.Length(255, varying = true))
  val modifiedBy: Rep[String] = column[String]("modified_by", O.Length(255, varying = true))
  val owner: Rep[String] = column[String]("owner", O.Length(255,varying=true))
  val organizationId: Rep[UUID] = column[UUID]("organization_id")
  val title: Rep[String] = column[String]("title", O.Length(255, varying = true))
  val description: Rep[String] = column[String]("description")
  val requirements: Rep[String] = column[String]("requirements")
  val license: Rep[String] = column[String]("license")
  val visibility: Rep[Visibility] = column[Visibility]("visibility")
  val compatibleDataSources: Rep[List[String]] = column[List[String]]("compatible_data_sources",
    O.Length(Int.MaxValue, varying = false), O.Default(List.empty))
  val stars: Rep[Float] = column[Float]("stars", O.Default(0.0f))
  val definition: Rep[Json] = column[Json]("definition", O.Length(2147483647,varying=false))

  lazy val organizationsFk =
    foreignKey("tools_organization_id_fkey", organizationId, Organizations)(
      r => r.id,
      onUpdate = ForeignKeyAction.NoAction,
      onDelete = ForeignKeyAction.NoAction)
  lazy val createdByUserFK = foreignKey("tools_created_by_fkey", createdBy, Users)(
    r => r.id,
    onUpdate = ForeignKeyAction.NoAction,
    onDelete = ForeignKeyAction.NoAction)
  lazy val modifiedByUserFK = foreignKey("tools_modified_by_fkey", modifiedBy, Users)(
    r => r.id,
    onUpdate = ForeignKeyAction.NoAction,
    onDelete = ForeignKeyAction.NoAction)
  lazy val ownerUserFK = foreignKey("tools_owner_fkey", owner, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
}

object Tools extends TableQuery(tag => new Tools(tag)) with LazyLogging {
  type TableQuery = Query[Tools, Tool, Seq]

  implicit val toolsSorter: QuerySorter[Tools] =
    new QuerySorter(
      new ToolFieldsSort(identity[Tools]),
      new OrganizationFkSort(identity[Tools]),
      new VisibilitySort(identity[Tools]),
      new TimestampSort(identity[Tools]))

  def joinRelated(query: Query[Tools, Tool, Seq])(implicit database: DB) = {
    logger.debug(s"Performing Tools related join -- SQL: ${query.result.statements.headOption}")

    val toolRelatedJoin = (query
      joinLeft ToolTagsToTools on { case (m, t) =>  m.id === t.toolId }
      joinLeft ToolTags on { case ((_, t), tt) => t.map(_.toolTagId) === tt.id }
      joinLeft ToolCategoriesToTools on { case (((m, _), _), c) =>  m.id === c.toolId }
      joinLeft ToolCategories on { case ((((_, _), _), c), cc) => c.map(_.toolCategorySlug) === cc.slugLabel }
      joinLeft Organizations on { case (((((m, _), _), _), _), o) => m.organizationId === o.id }
    )

    for {
      (((((tool, toolTagToTool), toolTag), toolCategoryToTool), toolCategory), organization) <- toolRelatedJoin
    } yield (
      tool,
      toolTag,
      toolCategory,
      organization
    )
  }

  def groupByTool(joins: Seq[Tool.ToolRelationshipJoin]): Seq[Tool.WithRelated] =
    joins.groupBy(_.tool).map { case (tool, toolRelationshipJoins) =>
      Tool.WithRelated(
        tool.id,
        tool.createdAt,
        tool.modifiedAt,
        tool.createdBy,
        tool.modifiedBy,
        tool.owner,
        toolRelationshipJoins.flatMap(_.organization).headOption,
        tool.title,
        tool.description,
        tool.requirements,
        tool.license,
        tool.visibility,
        tool.compatibleDataSources,
        tool.stars,
        tool.definition,
        toolRelationshipJoins.flatMap(_.toolTag).distinct,
        toolRelationshipJoins.flatMap(_.toolCategory).distinct
      )
    }.toSeq

  /**
    * Returns a paginated result with Tools
    *
    * TODO: filtering not yet implemented
    *
    * @param page page request that has limit, offset, and sort parameters
    */
  def listTools(page: PageRequest, user: User)(implicit database: DB):
      Future[PaginatedResponse[Tool.WithRelated]] = {

    val accessibleTools = Tools.filterUserVisibility(user)

    val pagedTools = accessibleTools
      .sort(page.sort)
      .drop(page.offset * page.limit)
      .take(page.limit)

    val toolsQueryAction = joinRelated(pagedTools).result

    logger.debug(s"Fetching tools -- SQL: ${toolsQueryAction.statements.headOption}")
    val toolsQueryResult = database.db.run {
      toolsQueryAction
    } map {
      joinTuples => joinTuples.map(joinTuple => Tool.ToolRelationshipJoin.tupled(joinTuple))
    } map {
      groupByTool
    }

    val nToolsAction = accessibleTools.length.result
    logger.debug(s"Counting tools -- SQL: ${nToolsAction.statements.headOption}")
    val totalToolsResult = database.db.run {
      nToolsAction
    }

    for {
      totalTools <- totalToolsResult
      tools <- toolsQueryResult
    } yield {
      val hasNext = (page.offset + 1) * page.limit < totalTools // 0 indexed page offset
      val hasPrevious = page.offset > 0
      PaginatedResponse(totalTools, hasPrevious, hasNext, page.offset, page.limit, tools)
    }
  }

  /** Retrieve a single tool from the database
    *
    * @param toolId java.util.UUID ID of tool to query
    * @param user   Results will be limited to user's organization
    */
  def getTool(toolId: UUID, user: User)(implicit database: DB): Future[Option[Tool.WithRelated]] = {
    val test = joinRelated(Tools.filter(_.id === toolId)).result
    logger.debug(s"Fetching a tool -- SQL: ${test.statements.headOption}")

    database.db.run {
      joinRelated(
        Tools
          .filterUserVisibility(user)
          .filter(_.id === toolId)
      ).result
    } map {
      joinTuples => joinTuples.map(joinTuple => Tool.ToolRelationshipJoin.tupled(joinTuple))
    } map {
      groupByTool
    } map {
      _.headOption
    }
  }

  /** Insert a tool given a create case class with a user. Includes tool tag/category ids.
    *
    * @param tooltoCreate Tool.Create object to use to create full tool
    * @param userId String user/owner to create a new tool with
    */
  def insertTool(tooltoCreate: Tool.Create, user: User)(
                  implicit database: DB): Future[Tool.WithRelatedUUIDs] = {

    val (tool, toolTagToTools, toolCategoryToTools) = tooltoCreate
      .toToolWithRelatedTuple(user)

    val toolInsertAction = Tools.forceInsert(tool)
    val toolTagToToolsInsertAction = ToolTagsToTools.forceInsertAll(toolTagToTools)
    val toolCategoryToToolsInsertAction = ToolCategoriesToTools.forceInsertAll(
      toolCategoryToTools)

    logger.debug(s"Inserting Tool -- SQL: ${toolInsertAction.statements.headOption}")
    logger.debug(
      s"Inserting tag join -- SQL: ${toolTagToToolsInsertAction.statements.headOption}")
    logger.debug(
      s"Inserting category join -- SQL: ${toolCategoryToToolsInsertAction.statements.headOption}")

    val insertAction = (
      for {
        toolInsert <- toolInsertAction
        toolTagToToolsInsert <- toolTagToToolsInsertAction
        toolCategoryToToolsInsert <- toolCategoryToToolsInsertAction
      } yield (toolInsert, toolTagToToolsInsert, toolCategoryToToolsInsert)).transactionally
    database.db.run {
      insertAction
    } map { _ =>
      tool.withRelatedFromComponentUUIDs(toolTagToTools.map(_.toolTagId),
        toolCategoryToTools.map(_.toolCategorySlug))
    }
  }

  /** Delete a given tool
    *
    * @param toolId UUID ID of tool to delete
    * @param user   Results will be limited to user's organization
    */
  def deleteTool(toolId: UUID, user: User)(implicit database: DB): Future[Int] = {
    database.db.run {
      Tools
        .filterToSharedOrganizationIfNotInRoot(user)
        .filter(_.id === toolId)
        .delete
    }
  }

  /** Update a given tool
    *
    * Currently allows updating the following attributes of a tool:
    *  - title
    *  - description
    *  - visibility
    *  - requirements
    *  - license
    *  - compatibleDataSources
    *  - stars
    *
    * TODO: updating tags/categories not yet implemented
    *
    * @param tool Tool model with updated values
    * @param toolId UUID primary key of tool to update
    * @param user User user updating tool values
    */
  def updateTool(tool: Tool, toolId: UUID, user: User)(implicit database: DB): Future[Int] = {

    val updateTime = new Timestamp((new java.util.Date).getTime)

    val updateToolQuery = for {
      updateTool <- Tools
                      .filterToSharedOrganizationIfNotInRoot(user)
                      .filter(_.id === toolId)
    } yield (
      updateTool.modifiedAt, updateTool.modifiedBy, updateTool.title,
      updateTool.description, updateTool.requirements, updateTool.license,
      updateTool.visibility, updateTool.compatibleDataSources, updateTool.stars
    )
    database.db.run {
      updateToolQuery.update((
        updateTime, user.id, tool.title,
        tool.description, tool.requirements, tool.license,
        tool.visibility, tool.compatibleDataSources, tool.stars
      ))
    } map {
      case 1 => 1
      case c => throw new IllegalStateException(
        s"Error updating tool: update result expected to be 1, was $c")
    }
  }
}
