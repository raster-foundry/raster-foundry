package com.azavea.rf.api.team

import com.azavea.rf.common.{Authentication, CommonHandlers, UserErrorHandler}
import com.azavea.rf.database.TeamDao
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
    pathEndOrSingleSlash {
      get {
        traceName("teams-list") {
          listTeams
        }
      } ~
      post {
        traceName("teams-create") {
          createTeam
        }
      }
    } ~
    pathPrefix(JavaUUID) { teamId =>
      get {
        traceName("team-detail") {
          getTeam(teamId)
        }
      } ~
      put {
        traceName("team-update") {
          updateTeam(teamId)
        }
      } ~
      delete {
        traceName("team-delete") {
          deleteTeam(teamId)
        }
      }
    }
  }

  def listTeams: Route = authenticate { user =>
    withPagination { page =>
      complete {
        TeamDao.query.page(page).transact(xa).unsafeToFuture
      }
    }
  }

  def createTeam: Route = authenticate { user =>
    entity(as[Team.Create]) { newTeamCreate =>
      onSuccess(TeamDao.create(newTeamCreate.toTeam(user)).transact(xa).unsafeToFuture()) { team =>
        complete(StatusCodes.Created, team)
      }
    }
  }

  def getTeam(teamId: UUID): Route = authenticate { user =>
    rejectEmptyResponse {
      complete {
        TeamDao.query.filter(teamId).selectOption.transact(xa).unsafeToFuture
      }
    }
  }

  def updateTeam(teamId: UUID): Route = authenticate { user =>
    entity(as[Team]) { updatedTeam =>
      onSuccess(TeamDao.update(updatedTeam, teamId, user).transact(xa).unsafeToFuture()) { team =>
        complete(StatusCodes.OK, team)
      }
    }
  }

  def deleteTeam(teamId: UUID): Route = authenticate { user =>
    onSuccess(TeamDao.query.filter(teamId).delete.transact(xa).unsafeToFuture) {
      completeSingleOrNotFound
    }
  }

}
