package com.rasterfoundry.api.featureflags

import akka.http.scaladsl.server.Route
import com.rasterfoundry.akkautil.{
  Authentication,
  CommonHandlers,
  UserErrorHandler
}
import com.rasterfoundry.database.FeatureFlagDao
import com.rasterfoundry.datamodel._
import com.lonelyplanet.akka.http.extensions.PaginationDirectives
import io.circe._
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import java.util.UUID

import cats.effect.IO
import doobie.util.transactor.Transactor
import com.rasterfoundry.database.filter.Filterables._
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._

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

  def getFeatureFlags: Route = authenticate { user =>
    complete(FeatureFlagDao.query.list.transact(xa).unsafeToFuture())
  }
}
