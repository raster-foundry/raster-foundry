package com.rasterfoundry.backsplash.server

import com.rasterfoundry.backsplash.error._
import com.rasterfoundry.database.{
  ProjectDao,
  ProjectLayerDao,
  SceneDao,
  ToolRunDao
}
import com.rasterfoundry.datamodel.{
  ActionType,
  AuthFailure,
  AuthResult,
  AuthSuccess,
  ObjectType,
  Project,
  Scene,
  ToolRun,
  User
}

import cats.Applicative
import cats.effect._
import com.colisweb.tracing.context.NoOpTracingContext
import com.colisweb.tracing.core.TracingContext
import com.typesafe.scalalogging.LazyLogging
import doobie.Transactor
import doobie.implicits._
import scalacache.CatsEffect.modes._
import scalacache.memoization._

import scala.concurrent.duration._

import java.util.UUID

class Authorizers(xa: Transactor[IO]) extends LazyLogging {

  implicit val authCache = Cache.caffeineAuthorizationCache
  implicit val sceneCache = Cache.sceneAuthCache
  implicit val projectCache = Cache.projectAuthCache
  implicit val toolRunCache = Cache.toolRunAuthCache
  implicit val flags = Cache.authenticationCacheFlags

  private def checkProjectAuthCached(user: User,
                                     projectId: UUID): IO[AuthResult[Project]] =
    memoizeF[IO, AuthResult[Project]](Some(60 seconds)) {
      logger.debug(
        s"Checking Project Auth User: ${user.id} => Project: ${projectId} with DB")
      ProjectDao
        .authorized(user, ObjectType.Project, projectId, ActionType.View)
        .transact(xa)
    }

  private def checkSceneAuthCached(user: User,
                                   sceneId: UUID): IO[AuthResult[Scene]] =
    memoizeF[IO, AuthResult[Scene]](Some(60 seconds)) {
      logger.debug(
        s"Checking Scene Auth User: ${user.id} => Scene: ${sceneId} with DB"
      )
      SceneDao
        .authorized(user, ObjectType.Scene, sceneId, ActionType.View)
        .transact(xa)
    }

  private def checkToolRunAuth(user: User,
                               toolRunId: UUID): IO[AuthResult[ToolRun]] =
    memoizeF[IO, AuthResult[ToolRun]](Some(10 seconds)) {
      logger.debug(
        s"Checking Tool Run Auth User: ${user.id} => Project: ${toolRunId} with DB")
      ToolRunDao
        .authorized(user, ObjectType.Analysis, toolRunId, ActionType.View)
        .transact(xa)
    }

  private def checkProjectLayerCached(
      projectID: UUID,
      layerID: UUID,
      tracingContext: TracingContext[IO]): IO[Boolean] = {
    val tags =
      Map("projectId" -> projectID.toString, "layerID" -> layerID.toString)
    tracingContext.span("checkProjectLayerCached", tags) use { childContext =>
      {
        memoizeF[IO, Boolean](Some(10 seconds)) {
          logger.debug(
            s"Checking whether layer ${layerID} is in project ${projectID}")
          childContext.span("layerIsInProject", tags) use (_ =>
            ProjectLayerDao.layerIsInProject(layerID, projectID).transact(xa))
        }
      }
    }
  }

  private def checkProjectAnalysisCached(projectId: UUID,
                                         analysisId: UUID): IO[Boolean] =
    memoizeF[IO, Boolean](Some(10 seconds)) {
      logger.debug(
        s"Checking whether analysis $analysisId references project $projectId")
      ToolRunDao.analysisReferencesProject(analysisId, projectId).transact(xa)
    }

  def authObject[T](f: (User, UUID) => IO[AuthResult[T]],
                    user: User,
                    objectId: UUID): IO[T] =
    f(user, objectId) flatMap {
      case AuthFailure() =>
        IO.raiseError[T](
          NotAuthorizedException(
            s"User ${user.id} is not authorized to view $objectId"
          ))
      case AuthSuccess(v) => IO.pure { v }
    }

  def authToolRun(user: User, toolRunId: UUID): IO[ToolRun] =
    authObject(checkToolRunAuth, user, toolRunId)

  def authProject(user: User, projectId: UUID): IO[Project] =
    authObject(checkProjectAuthCached, user, projectId)

  def authProject(user: User,
                  projectId: UUID,
                  tracingContext: TracingContext[IO]): IO[Project] =
    tracingContext.span(
      "authProject",
      Map("user" -> user.id, "projectId" -> projectId.toString)) use { _ =>
      authObject(checkProjectAuthCached, user, projectId)
    }

  def authScene(user: User, sceneId: UUID): IO[Scene] =
    authObject(checkSceneAuthCached, user, sceneId)

  def authProjectLayer(projectID: UUID,
                       layerID: UUID,
                       tracingContext: TracingContext[IO] =
                         NoOpTracingContext[IO]("no-op-read")): IO[Unit] = {
    checkProjectLayerCached(projectID, layerID, tracingContext) flatMap {
      case false =>
        IO.raiseError(
          NotAuthorizedException(
            s"Layer ${layerID} is not in project ${projectID}"
          ))
      case _ => IO.pure { () }
    }
  }

  def authProjectAnalysis(user: User,
                          projectId: UUID,
                          analysisId: UUID): IO[Unit] = {
    Applicative[IO].map2(
      checkProjectAuthCached(user, projectId),
      checkProjectAnalysisCached(projectId, analysisId))(_.toBoolean && _) map {
      case false =>
        throw NotAuthorizedException(
          s"Analysis $analysisId does not reference project $projectId"
        )
      case _ => ()
    }
  }
}
