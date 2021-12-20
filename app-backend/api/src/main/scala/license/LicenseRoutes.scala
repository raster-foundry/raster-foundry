package com.rasterfoundry.api.license

import com.rasterfoundry.akkautil.PaginationDirectives
import com.rasterfoundry.akkautil.{
  Authentication,
  CommonHandlers,
  MembershipAndUser,
  UserErrorHandler
}
import com.rasterfoundry.database.LicenseDao
import com.rasterfoundry.database.filter.Filterables._
import com.rasterfoundry.datamodel.{Action, Domain, ScopedAction}

import akka.http.scaladsl.server.Route
import cats.effect.IO
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import doobie._
import doobie.implicits._
import doobie.util.transactor.Transactor

trait LicenseRoutes
    extends Authentication
    with PaginationDirectives
    with CommonHandlers
    with UserErrorHandler {

  val xa: Transactor[IO]

  val licenseRoutes: Route = handleExceptions(userExceptionHandler) {
    pathEndOrSingleSlash {
      get { listLicenses }
    } ~ pathPrefix(Segment) { licenseShortName =>
      get { getLicense(licenseShortName) }
    }
  }

  def listLicenses: Route =
    authenticate {
      case MembershipAndUser(_, user) =>
        authorizeScope(ScopedAction(Domain.Licenses, Action.Read, None), user) {
          withPagination { pageRequest =>
            complete(
              LicenseDao.query.page(pageRequest).transact(xa).unsafeToFuture
            )
          }
        }
    }

  def getLicense(shortName: String): Route =
    authenticate {
      case MembershipAndUser(_, user) =>
        authorizeScope(ScopedAction(Domain.Licenses, Action.Read, None), user) {
          rejectEmptyResponse {
            complete(
              LicenseDao.query
                .filter(fr"short_name = ${shortName}")
                .selectOption
                .transact(xa)
                .unsafeToFuture
            )
          }
        }
    }
}
