package com.rasterfoundry.api.organization

import com.rasterfoundry.akkautil.{
  Authentication,
  CommonHandlers,
  UserErrorHandler
}
import com.rasterfoundry.database.OrganizationDao
import com.rasterfoundry.database.filter.Filterables._
import com.rasterfoundry.api.utils.Config
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes
import com.rasterfoundry.akkautil.PaginationDirectives
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import java.util.UUID

import cats.effect.IO
import com.rasterfoundry.datamodel.{Action, Domain, ScopedAction}
import doobie.util.transactor.Transactor
import doobie._
import doobie.implicits._

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
    authorizeScope(ScopedAction(Domain.Organizations, Action.Read, None), user) {
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
  }

  def searchOrganizations(): Route = authenticate { user =>
    authorizeScope(
      ScopedAction(Domain.Organizations, Action.Search, None),
      user
    ) {
      searchParams { (searchParams) =>
        complete {
          OrganizationDao
            .searchOrganizations(user, searchParams)
            .transact(xa)
            .unsafeToFuture
        }
      }
    }
  }

  def addOrganizationLogo(orgID: UUID): Route = authenticate { user =>
    authorizeScope(
      ScopedAction(Domain.Organizations, Action.Update, None),
      user
    ) {
      authorizeAsync(
        OrganizationDao.userIsAdmin(user, orgID).transact(xa).unsafeToFuture()
      ) {
        entity(as[String]) { logoBase64 =>
          onSuccess(
            OrganizationDao
              .addLogo(logoBase64, orgID, dataBucket)
              .transact(xa)
              .unsafeToFuture()
          ) { organization =>
            complete((StatusCodes.Created, organization))
          }
        }
      }
    }
  }

  // @TODO: There is no delete functionality as we most likely will want to instead deactivate organizations
}
