package com.rasterfoundry.api.team

import com.rasterfoundry.akkautil.PaginationDirectives
import com.rasterfoundry.akkautil.{
  Authentication,
  CommonHandlers,
  UserErrorHandler
}
import com.rasterfoundry.database._
import com.rasterfoundry.datamodel._

import akka.http.scaladsl.server.Route
import cats.data.OptionT
import cats.effect.IO
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import doobie._
import doobie.implicits._
import doobie.util.transactor.Transactor

import java.util.UUID

/**
  * Routes for Organizations
  */
trait TeamRoutes
    extends Authentication
    with PaginationDirectives
    with CommonHandlers
    with UserErrorHandler {

  val xa: Transactor[IO]

  val teamRoutes: Route = handleExceptions(userExceptionHandler) {
    pathPrefix(JavaUUID) { teamId =>
      get {
        getTeam(teamId)
      }
    }
  }

  def getTeam(teamId: UUID): Route = authenticate { user =>
    authorizeScope(ScopedAction(Domain.Teams, Action.Read, None), user) {
      authorizeAsync {
        val authIO = for {
          teamMember <- OptionT.liftF[ConnectionIO, Boolean](
            TeamDao.userIsMember(user, teamId)
          )
          team <- OptionT[ConnectionIO, Team](TeamDao.getTeamById(teamId))
          organization <- OptionT[ConnectionIO, Organization](
            OrganizationDao.getOrganizationById(team.organizationId)
          )
          platformAdmin <- OptionT.liftF[ConnectionIO, Boolean](
            PlatformDao.userIsAdmin(user, organization.platformId)
          )
          organizationMember <- OptionT.liftF[ConnectionIO, Boolean](
            OrganizationDao.userIsMember(user, organization.id)
          )
        } yield {
          teamMember || organizationMember || platformAdmin
        }
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

}
