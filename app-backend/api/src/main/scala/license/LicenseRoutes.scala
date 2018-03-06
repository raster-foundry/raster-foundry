package com.azavea.rf.api.license

import com.azavea.rf.common.{Authentication, CommonHandlers, UserErrorHandler}
import com.azavea.rf.database.LicenseDao
import akka.http.scaladsl.server.Route
import com.lonelyplanet.akka.http.extensions.PaginationDirectives
import io.circe._
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._

import cats.effect.IO
import doobie.util.transactor.Transactor
import com.azavea.rf.database.filter.Filterables._
import com.azavea.rf.datamodel._
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._


trait LicenseRoutes extends Authentication
  with PaginationDirectives
  with CommonHandlers
  with UserErrorHandler {

  implicit def xa: Transactor[IO]

  val licenseRoutes: Route = handleExceptions(userExceptionHandler) {
    pathEndOrSingleSlash {
      get { listLicenses }
    } ~ pathPrefix(Segment) { licenseShortName =>
      get { getLicense(licenseShortName) }
    }
  }

  def listLicenses: Route = authenticate { user =>
    withPagination { pageRequest =>
      complete(LicenseDao.query.page(pageRequest).transact(xa).unsafeToFuture)
    }
  }

  def getLicense(shortName: String): Route = authenticate { user =>
    rejectEmptyResponse {
      complete(LicenseDao.query.filter(fr"short_name = ${shortName}").selectOption.transact(xa).unsafeToFuture)
    }
  }
}
