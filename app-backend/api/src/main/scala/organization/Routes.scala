package com.azavea.rf.api.organization

import com.azavea.rf.authentication.Authentication
import com.azavea.rf.common.{CommonHandlers, UserErrorHandler}
import com.azavea.rf.database.OrganizationDao
import com.azavea.rf.database.filter.Filterables._
import com.azavea.rf.datamodel._
import com.azavea.rf.api.utils.Config
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

trait OrganizationRoutes
    extends Authentication
    with Config
    with PaginationDirectives
    with CommonHandlers
    with UserErrorHandler
    with OrganizationQueryParameterDirective {

  val xa: Transactor[IO]

  val organizationRoutes: Route = handleExceptions(userExceptionHandler) {
    pathPrefix(JavaUUID) { orgId =>
      pathEndOrSingleSlash {
        get { getOrganization(orgId) }
      } ~
        pathPrefix("logo") {
          pathEndOrSingleSlash {
            post { addOrganizationLogo(orgId) }
          }
        }
    } ~ pathPrefix("search") {
      pathEndOrSingleSlash {
        get { searchOrganizations() }
      }
    }
  }

  def getOrganization(orgId: UUID): Route = authenticate { user =>
    rejectEmptyResponse {
      complete {
        OrganizationDao
          .viewFilter(user)
          .filter(orgId)
          .selectOption
          .transact(xa)
          .unsafeToFuture()
      }
    }
  }

  def searchOrganizations(): Route = authenticate { user =>
    searchParams { (searchParams) =>
      complete {
        OrganizationDao
          .searchOrganizations(user, searchParams)
          .transact(xa)
          .unsafeToFuture
      }
    }
  }

  def addOrganizationLogo(orgID: UUID): Route = authenticate { user =>
    authorizeAsync(
      OrganizationDao.userIsAdmin(user, orgID).transact(xa).unsafeToFuture()
    ) {
      entity(as[String]) { logoBase64 =>
        onSuccess(
          OrganizationDao
            .addLogo(logoBase64, orgID, dataBucket)
            .transact(xa)
            .unsafeToFuture()) { organization =>
          complete((StatusCodes.Created, organization))
        }
      }
    }
  }

  // @TODO: There is no delete functionality as we most likely will want to instead deactivate organizations
}
