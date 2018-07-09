package com.azavea.rf.api.team

import com.azavea.rf.authentication.Authentication
import com.azavea.rf.common.{CommonHandlers, UserErrorHandler}
import com.azavea.rf.database._
import com.azavea.rf.database.filter.Filterables._
import com.azavea.rf.datamodel._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes
import com.lonelyplanet.akka.http.extensions.PaginationDirectives
import io.circe._
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import java.util.UUID

import cats.effect.IO

import doobie.util.transactor.Transactor
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.Fragments.in
import doobie.postgres._
import doobie.postgres.implicits._

import kamon.akka.http.KamonTraceDirectives


/**
  * Routes for Organizations
  */
trait TeamRoutes extends Authentication
    with PaginationDirectives
    with CommonHandlers
    with UserErrorHandler
    with KamonTraceDirectives {

  val xa: Transactor[IO]

  val teamRoutes: Route = handleExceptions(userExceptionHandler) {
    pathPrefix(JavaUUID) { teamId =>
      get {
        traceName("team-detail") {
          getTeam(teamId)
        }
      }
    }
  }

  def getTeam(teamId: UUID): Route = authenticate { user =>
    authorizeAsync {
      val authIO = for {
        teamO <- TeamDao.getTeamById(teamId)
        organizationO <- teamO match {
          case Some(team) => OrganizationDao.getOrganizationById(team.organizationId)
          case _ => None.pure[ConnectionIO]
        }
        platformAdmin <- organizationO match {
          case Some(org) => PlatformDao.userIsAdmin(user, org.platformId)
          case _ => false.pure[ConnectionIO]
        }
        organizationMember <- organizationO match {
          case Some(org) => OrganizationDao.userIsMember(user, org.id)
          case _ => false.pure[ConnectionIO]
        }
        teamMember <- TeamDao.userIsMember(user, teamId)
      } yield { teamMember || organizationMember || platformAdmin }
      authIO.transact(xa).unsafeToFuture
    } {
      rejectEmptyResponse {
        complete {
          TeamDao.getTeamById(teamId).transact(xa).unsafeToFuture
        }
      }
    }
  }

}
