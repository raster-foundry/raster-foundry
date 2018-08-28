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

import cats.data.OptionT
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
trait TeamRoutes
    extends Authentication
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
        teamMember <- OptionT.liftF[ConnectionIO, Boolean](
          TeamDao.userIsMember(user, teamId))
        team <- OptionT[ConnectionIO, Team](TeamDao.getTeamById(teamId))
        organization <- OptionT[ConnectionIO, Organization](
          OrganizationDao.getOrganizationById(team.organizationId)
        )
        platformAdmin <- OptionT.liftF[ConnectionIO, Boolean](
          PlatformDao.userIsAdmin(user, organization.platformId))
        organizationMember <- OptionT.liftF[ConnectionIO, Boolean](
          OrganizationDao.userIsMember(user, organization.id))
      } yield { teamMember || organizationMember || platformAdmin }
      authIO.value.map(_.getOrElse(false)).transact(xa).unsafeToFuture
    } {
      rejectEmptyResponse {
        complete {
          TeamDao.getTeamById(teamId).transact(xa).unsafeToFuture
        }
      }
    }
  }

}
