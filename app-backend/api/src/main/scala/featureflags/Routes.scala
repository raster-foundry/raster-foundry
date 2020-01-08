package com.rasterfoundry.api.featureflags

import akka.http.scaladsl.server.Route
import com.rasterfoundry.akkautil.{
  Authentication,
  CommonHandlers,
  UserErrorHandler
}
import com.rasterfoundry.database.FeatureFlagDao
import com.rasterfoundry.akkautil.PaginationDirectives
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import cats.effect.IO
import com.rasterfoundry.datamodel.{Action, Domain, ScopedAction, User}
import doobie.util.transactor.Transactor
import doobie._
import doobie.implicits._

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

  def getFeatureFlags: Route = authenticate { user: User =>
    authorizeScope(ScopedAction(Domain.FeatureFlags, Action.Read, None), user) {
      complete(FeatureFlagDao.query.list.transact(xa).unsafeToFuture())
    }
  }
}
