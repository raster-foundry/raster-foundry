package com.rasterfoundry.api.project

import com.rasterfoundry.akkautil.Authentication
import com.rasterfoundry.database.{ProjectDao, ToolRunDao, UserDao, MapTokenDao}
import com.rasterfoundry.datamodel._

import cats.Applicative
import cats.effect.IO
import cats.implicits._
import akka.http.scaladsl.server._
import doobie.Transactor
import doobie.implicits._
import doobie.ConnectionIO

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import java.util.UUID

trait ProjectAuthorizationDirectives extends Authentication with Directives {

  implicit val xa: Transactor[IO]

  def projectIsPublic(projectId: UUID): Directive0 = {
    authorizeAsync {
      ProjectDao
        .unsafeGetProjectById(projectId)
        .transact(xa)
        .unsafeToFuture map {
        _.visibility == Visibility.Public
      }
    }
  }

  def projectAuthFromMapTokenO(
      mapTokenO: Option[UUID],
      projectId: UUID
  ): Directive0 = {
    mapTokenO map { mapToken =>
      authorizeAsync {
        MapTokenDao
          .checkProject(projectId)(mapToken)
          .transact(xa)
          .map({
            case Some(_) => true
            case _       => false
          })
          .unsafeToFuture
      }
    } getOrElse { reject(AuthorizationFailedRejection) }
  }

  def projectAuthFromTokenO(
      tokenO: Option[String],
      projectId: UUID,
      analysisId: Option[UUID] = None,
      scopedAction: ScopedAction
  ): Directive0 = {
    authorizeAsync {
      tokenO map { token =>
        verifyJWT(token.split(" ").last) traverse {
          case (_, jwtClaims) =>
            val userId = jwtClaims.getStringClaim("sub")
            for {
              user <- UserDao.unsafeGetUserById(userId)
              projectAuth <- ProjectDao.authorized(
                user,
                ObjectType.Project,
                projectId,
                ActionType.View
              )
              authResult <- (projectAuth, analysisId) match {
                case (AuthFailure(), Some(id: UUID)) =>
                  ToolRunDao.authorizeReferencedProject(user, id, projectId)
                case (_, _) =>
                  Applicative[ConnectionIO].pure(projectAuth.toBoolean)
              }
            } yield authResult && user.scope.actions.contains(scopedAction)
        } map {
          case Right(result) => result
          case Left(_)       => false
        } transact (xa) unsafeToFuture
      } getOrElse { Future.successful(false) }
    }
  }
}
