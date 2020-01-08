package com.rasterfoundry.api.license

import com.rasterfoundry.akkautil.{
  Authentication,
  CommonHandlers,
  UserErrorHandler
}
import com.rasterfoundry.database.LicenseDao
import akka.http.scaladsl.server.Route
import com.rasterfoundry.akkautil.PaginationDirectives
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import cats.effect.IO
import doobie.util.transactor.Transactor
import com.rasterfoundry.database.filter.Filterables._
import com.rasterfoundry.datamodel.{Action, Domain, ScopedAction, User}
import doobie._
import doobie.implicits._

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

  def listLicenses: Route = authenticate { user: User =>
    authorizeScope(ScopedAction(Domain.Licenses, Action.Read, None), user) {
      withPagination { pageRequest =>
        complete(LicenseDao.query.page(pageRequest).transact(xa).unsafeToFuture)
      }
    }
  }

  def getLicense(shortName: String): Route = authenticate { user: User =>
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
