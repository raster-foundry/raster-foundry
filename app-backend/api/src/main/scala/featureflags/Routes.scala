package com.rasterfoundry.api.featureflags

import com.rasterfoundry.akkautil.PaginationDirectives
import com.rasterfoundry.akkautil.{
  Authentication,
  CommonHandlers,
  UserErrorHandler
}
import com.rasterfoundry.database.FeatureFlagDao
import com.rasterfoundry.datamodel.{Action, Domain, ScopedAction}

import akka.http.scaladsl.server.Route
import cats.effect.IO
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import doobie._
import doobie.implicits._
import doobie.util.transactor.Transactor

/**
  * Routes for FeatureFlag overrides
  */
trait FeatureFlagRoutes
    extends Authentication
    with PaginationDirectives
    with CommonHandlers
    with UserErrorHandler {

  val xa: Transactor[IO]

  val featureFlagRoutes: Route = handleExceptions(userExceptionHandler) {
    pathEndOrSingleSlash {
      get { getFeatureFlags }
    }
  }

  def getFeatureFlags: Route =
    authenticate {
      case (user, _) =>
        authorizeScope(
          ScopedAction(Domain.FeatureFlags, Action.Read, None),
          user
        ) {
          complete(FeatureFlagDao.query.list.transact(xa).unsafeToFuture())
        }
    }
}
