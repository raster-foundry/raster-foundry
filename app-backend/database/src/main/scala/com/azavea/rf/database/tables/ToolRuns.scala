package com.azavea.rf.database.tables

import java.util.UUID
import java.sql.Timestamp

import com.typesafe.scalalogging.LazyLogging

import slick.model.ForeignKeyAction
import slick.dbio.DBIO

import com.azavea.rf.datamodel._
import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.database.fields._
import com.azavea.rf.database.query._

class ToolRuns(_TableTag: Tag) extends Table[ToolRun](_TableTag, "tool_runs")
    with TimestampFields {
  def * = (id, createdAt, createdBy, modifiedAt, modifiedBy, projectId, toolId, execution_parameters) <>
    (ToolRun.tupled, ToolRun.unapply _)

  val id: Rep[UUID]  = column[UUID]("id", O.PrimaryKey)
  val createdAt: Rep[Timestamp] = column[Timestamp]("created_at")
  val createdBy: Rep[String] = column[String]("created_by")
  val modifiedAt: Rep[Timestamp] = column[Timestamp]("modified_at")
  val modifiedBy: Rep[String] = column[String]("modified_by")
  val projectId: Rep[UUID] = column[UUID]("project")
  val toolId: Rep[UUID] = column[UUID]("tool")
  val execution_parameters: Rep[Map[String, Any]] = column[Map[String, Any]]("execution_parameters")

  lazy val createdByUserFK = foreignKey("tool_runs_created_by_fkey", createdBy, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  lazy val modifiedByUserFK = foreignKey("tool_runs_modified_by_fkey", createdBy, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  lazy val projectFK = foreignKey("tool_runs_project_fkey", createdBy, Projects)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  lazy val toolFK = foreignKey("tool_runs_tool_fkey", createdBy, Tools)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)

}

object ToolRuns extends TableQuery(tag => new ToolRuns(tag)) with LazyLogging {
  type TableQuery = Query[ToolRuns, ToolRun, Seq]

  implicit class withToolRunsTableQuery[M, U, C[_]](toolruns: ToolRuns.TableQuery) extends
      ToolRunDefaultQuery[M, U, C](toolruns)

  def insertToolRun(tr: ToolRun.Create, userId: String): DBIO[ToolRun] =
    (ToolRuns returning ToolRuns).forceInsert(tr.toToolRun(userId))

  def getToolRun(id: UUID): DBIO[Option[ToolRun]] =
    ToolRuns.filter(_.id === id).result.headOption

  def listToolRuns(offset: Int, limit: Int,
                   toolRunParams: CombinedToolRunQueryParameters): ListQueryResult[ToolRun] = {
    val dropRecords = limit * offset
    val toolRunFilterQuery = ToolRuns
      .filterByTimestamp(toolRunParams.timestampParams)
      .filterByToolRunParams(toolRunParams.toolRunParams)

    ListQueryResult[ToolRun](
      (toolRunFilterQuery
         .drop(dropRecords)
         .take(limit)
         .result):DBIO[Seq[ToolRun]],
      ToolRuns.length.result
    )
  }

  def updateToolRun(tr: ToolRun, id: UUID, user: User): DBIO[Int] = {
    val updateTime = new Timestamp((new java.util.Date).getTime)

    val updateToolRunQuery = for {
      updateToolRun <- ToolRuns.filter(_.id === id)
    } yield (
      updateToolRun.modifiedAt,
      updateToolRun.modifiedBy,
      updateToolRun.execution_parameters
    )

    updateToolRunQuery.update(
      updateTime,
      user.id,
      tr.execution_parameters
    )
  }

  def deleteToolRun(id: UUID): DBIO[Int] =
    ToolRuns.filter(_.id === id).delete
}

class ToolRunDefaultQuery[M, U, C[_]](toolruns: ToolRuns.TableQuery) {
  def filterByToolRunParams(toolRunParams: ToolRunQueryParameters): ToolRuns.TableQuery = {
    toolruns.filter { toolRun =>
      toolRunParams.createdBy
        .map(toolRun.createdBy === _)
        .reduceLeftOption(_ || _)
        .getOrElse(true: Rep[Boolean])
    }.filter { toolRun =>
      toolRunParams.projectId
        .map(toolRun.projectId === _)
        .reduceLeftOption(_ || _)
        .getOrElse(true: Rep[Boolean])
    }.filter { toolRun =>
      toolRunParams.toolId
        .map(toolRun.toolId === _)
        .reduceLeftOption(_ || _)
        .getOrElse(true: Rep[Boolean])
    }
  }
}
