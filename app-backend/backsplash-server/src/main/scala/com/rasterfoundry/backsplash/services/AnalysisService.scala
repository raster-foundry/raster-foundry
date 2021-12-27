package com.rasterfoundry.backsplash.server

import com.rasterfoundry.backsplash.Parameters._
import com.rasterfoundry.backsplash.utils.ResponseUtils
import com.rasterfoundry.datamodel.UserWithPlatform

import cats.effect.IO
import org.http4s._
import org.http4s.dsl.io._

class AnalysisService[Param, HistStore](
    analysisManager: AnalysisManager[Param, HistStore]
) extends ResponseUtils {

  val routes: AuthedRoutes[UserWithPlatform, IO] =
    AuthedRoutes.of {
      case GET -> Root / UUIDWrapper(analysisId) / "histogram"
            :? NodeQueryParamMatcher(node) as user =>
        analysisManager
          .histogram(user.toUser, analysisId, node)
          .map(addTempPlatformInfo(_, user.platformNameOpt, user.platformIdOpt))

      case GET -> Root / UUIDWrapper(analysisId) / "statistics"
            :? NodeQueryParamMatcher(node) as user =>
        analysisManager
          .statistics(user.toUser, analysisId, node)
          .map(addTempPlatformInfo(_, user.platformNameOpt, user.platformIdOpt))

      case GET -> Root / UUIDWrapper(analysisId) / IntVar(z) / IntVar(
            x
          ) / IntVar(y)
            :? NodeQueryParamMatcher(node) as user =>
        analysisManager
          .tile(user.toUser, analysisId, node, z, x, y)
          .map(addTempPlatformInfo(_, user.platformNameOpt, user.platformIdOpt))

      case authedReq @ GET -> Root / UUIDWrapper(analysisId) / "raw"
            :? ExtentQueryParamMatcher(extent)
            :? ZoomQueryParamMatcher(zoom)
            :? NodeQueryParamMatcher(node) as user =>
        analysisManager
          .export(
            authedReq,
            user.toUser,
            analysisId,
            node,
            extent,
            zoom
          )
          .map(addTempPlatformInfo(_, user.platformNameOpt, user.platformIdOpt))
    }
}
