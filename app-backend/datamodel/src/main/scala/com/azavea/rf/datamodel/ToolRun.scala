package com.azavea.rf.datamodel

import spray.json._
import spray.json.DefaultJsonProtocol._
import java.util.UUID
import java.sql.Timestamp

case class ToolRun(
  id: UUID,
  createdAt: Timestamp,
  createdBy: String,
  modifiedAt: Timestamp,
  modifiedBy: String,
  project: UUID,
  tool: UUID,
  execution_parameters: Map[String, Any]
)

object ToolRun {
  def create = Create.apply _
  def tupled = (ToolRun.apply _).tupled

  implicit def defaultToolRunFormat = jsonFormat8(ToolRun.apply _)

  case class Create(
    project: UUID,
    tool: UUID,
    execution_parameters: Map[String, Any]
  ) {
    def toToolRun(userId: String): ToolRun = {
      val now = new Timestamp((new java.util.Date).getTime)
      ToolRun(
        UUID.randomUUID,
        now,
        userId,
        now,
        userId,
        project,
        tool,
        execution_parameters
      )
    }
  }

  object Create {
    implicit val defaultToolRunCreateFormat = jsonFormat3(Create.apply _)
  }
}
