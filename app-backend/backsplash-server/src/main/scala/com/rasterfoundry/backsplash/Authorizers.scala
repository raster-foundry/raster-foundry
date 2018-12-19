package com.rasterfoundry.backsplash.server

import com.rasterfoundry.backsplash.error._
import com.rasterfoundry.datamodel.{ActionType, ObjectType, User}
import com.rasterfoundry.database.{ProjectDao, ToolRunDao}
import com.rasterfoundry.database.util.RFTransactor

import cats.effect._

import doobie.Transactor
import doobie.implicits._

import java.util.UUID

object Authorizers {
  val xa = RFTransactor.xa

  def authToolRun(user: User, toolRunId: UUID): IO[Unit] =
    ToolRunDao
      .authorized(user, ObjectType.Analysis, toolRunId, ActionType.View)
      .transact(xa) map {
      case false =>
        throw NotAuthorizedException(
          s"User ${user.id} is not authorized to view analysis $toolRunId")
      case _ => ()
    }

  def authProject(user: User, projectId: UUID): IO[Unit] =
    ProjectDao
      .authorized(user, ObjectType.Project, projectId, ActionType.View)
      .transact(xa) map {
      case false =>
        throw NotAuthorizedException(
          s"User ${user.id} is not authorized to view project $projectId")
      case _ => ()
    }
}
