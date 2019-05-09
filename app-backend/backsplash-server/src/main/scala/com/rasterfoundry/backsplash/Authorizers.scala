package com.rasterfoundry.backsplash.server

import com.rasterfoundry.backsplash.error._
import com.rasterfoundry.datamodel.{ActionType, ObjectType, User}
import com.rasterfoundry.database.{
  ProjectDao,
  ProjectLayerDao,
  SceneDao,
  ToolRunDao
}

import cats.Applicative
import cats.effect._
import doobie.Transactor
import doobie.implicits._
import com.typesafe.scalalogging.LazyLogging
import scalacache.memoization._
import scalacache.CatsEffect.modes._

import scala.concurrent.duration._
import java.util.UUID

class Authorizers(xa: Transactor[IO]) extends LazyLogging {

  implicit val authCache = Cache.caffeineAuthorizationCache
  implicit val flags = Cache.authenticationCacheFlags

  private def checkProjectAuthCached(user: User, projectId: UUID): IO[Boolean] =
    memoizeF[IO, Boolean](Some(60.seconds)) {
      logger.debug(
        s"Checking Project Auth User: ${user.id} => Project: ${projectId} with DB")
      ProjectDao
        .authorized(user, ObjectType.Project, projectId, ActionType.View)
        .transact(xa)
    }

  private def checkSceneAuthCached(user: User, sceneId: UUID): IO[Boolean] =
    memoizeF[IO, Boolean](Some(60.seconds)) {
      logger.debug(
        s"Checking Scene Auth User: ${user.id} => Scene: ${sceneId} with DB"
      )
      SceneDao
        .authorized(user, ObjectType.Scene, sceneId, ActionType.View)
        .transact(xa)
    }

  private def checkToolRunAuth(user: User, toolRunId: UUID): IO[Boolean] =
    memoizeF[IO, Boolean](Some(10.seconds)) {
      logger.debug(
        s"Checking Tool Run Auth User: ${user.id} => Project: ${toolRunId} with DB")
      ToolRunDao
        .authorized(user, ObjectType.Analysis, toolRunId, ActionType.View)
        .transact(xa)
    }

  private def checkProjectLayerCached(projectID: UUID,
                                      layerID: UUID): IO[Boolean] =
    memoizeF[IO, Boolean](Some(10.seconds)) {
      logger.debug(
        s"Checking whether layer ${layerID} is in project ${projectID}")
      ProjectLayerDao.layerIsInProject(layerID, projectID).transact(xa)
    }

  private def checkProjectAnalysisCached(projectId: UUID,
                                         analysisId: UUID): IO[Boolean] =
    memoizeF[IO, Boolean](Some(10.seconds)) {
      logger.debug(
        s"Checking whether analysis $analysisId references project $projectId")
      ToolRunDao.analysisReferencesProject(analysisId, projectId).transact(xa)
    }

  def authToolRun(user: User, toolRunId: UUID): IO[Unit] = {
    checkToolRunAuth(user, toolRunId) map {
      case false =>
        throw NotAuthorizedException(
          s"User ${user.id} is not authorized to view analysis $toolRunId"
        )
      case _ => ()
    }
  }

  def authProject(user: User, projectId: UUID): IO[Unit] = {
    checkProjectAuthCached(user, projectId) map {
      case false =>
        throw NotAuthorizedException(
          s"User ${user.id} is not authorized to view project $projectId"
        )
      case _ => ()
    }
  }

  def authScene(user: User, sceneId: UUID): IO[Unit] = {
    checkSceneAuthCached(user, sceneId) map {
      case false =>
        throw NotAuthorizedException(
          s"User ${user.id} is not authorized to view scene $sceneId"
        )
      case _ => ()
    }
  }

  def authProjectLayer(projectID: UUID, layerID: UUID): IO[Unit] = {
    checkProjectLayerCached(projectID, layerID) map {
      case false =>
        throw NotAuthorizedException(
          s"Layer ${layerID} is not in project ${projectID}"
        )
      case _ => ()
    }
  }

  def authProjectAnalysis(user: User,
                          projectId: UUID,
                          analysisId: UUID): IO[Unit] = {
    Applicative[IO].map2(
      checkProjectAuthCached(user, projectId),
      checkProjectAnalysisCached(projectId, analysisId))(_ && _) map {
      case false =>
        throw NotAuthorizedException(
          s"Analysis $analysisId does not reference project $projectId"
        )
      case _ => ()
    }
  }
}
