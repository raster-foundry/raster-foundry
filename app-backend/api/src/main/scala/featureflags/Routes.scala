package com.azavea.rf.api.featureflags

import akka.http.scaladsl.server.Route
import com.azavea.rf.common.{Authentication, CommonHandlers, UserErrorHandler}
import com.azavea.rf.database.FeatureFlagDao
import com.azavea.rf.datamodel._
import akka.http.scaladsl.server.Route
import com.lonelyplanet.akka.http.extensions.PaginationDirectives
import io.circe._
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import java.util.UUID

import cats.effect.IO
import doobie.util.transactor.Transactor
import com.azavea.rf.database.filter.Filterables._
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._


/**
  * Routes for FeatureFlag overrides
  */
trait FeatureFlagRoutes extends Authentication
  with PaginationDirectives
  with CommonHandlers
  with UserErrorHandler {

  implicit def xa: Transactor[IO]

  val featureFlagRoutes: Route = handleExceptions(userExceptionHandler) {
    pathEndOrSingleSlash {
      get { getFeatureFlags }
    }
  }

  def getFeatureFlags: Route = authenticate { user =>
    complete(FeatureFlagDao.query.list.transact(xa).unsafeToFuture())
  }
}
