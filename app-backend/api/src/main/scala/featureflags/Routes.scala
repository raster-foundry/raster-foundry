package com.azavea.rf.api.featureflags

import akka.http.scaladsl.server.Route
import com.azavea.rf.common.{Authentication, CommonHandlers, UserErrorHandler}
import com.azavea.rf.database.Database
import com.azavea.rf.database.tables.{OrgFeatureFlags}
import com.azavea.rf.datamodel._

import akka.http.scaladsl.server.Route
import com.lonelyplanet.akka.http.extensions.PaginationDirectives
import io.circe._
import de.heikoseeberger.akkahttpcirce.CirceSupport._

import java.util.UUID


/**
  * Routes for FeatureFlag overrides
  */
trait FeatureFlagRoutes extends Authentication
  with PaginationDirectives
  with CommonHandlers
  with UserErrorHandler {

  implicit def database: Database

  val featureFlagRoutes: Route = handleExceptions(userExceptionHandler) {
    pathEndOrSingleSlash {
      get { getFeatureFlags }
    }
  }

  def getFeatureFlags: Route = authenticate { user =>
    complete(OrgFeatureFlags.getFeatures(user.organizationId))
  }
}
