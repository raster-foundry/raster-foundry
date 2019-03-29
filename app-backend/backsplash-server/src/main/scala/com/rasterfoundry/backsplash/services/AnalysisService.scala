package com.rasterfoundry.backsplash.server

import com.rasterfoundry.common.datamodel.User
import com.rasterfoundry.backsplash.Parameters._

import cats.effect.IO
import org.http4s._
import org.http4s.dsl.io._

class AnalysisService[Param, HistStore](
    analysisManager: AnalysisManager[Param, HistStore]) {

  val routes: AuthedService[User, IO] =
    AuthedService {
      case GET -> Root / UUIDWrapper(analysisId) / "histogram"
            :? NodeQueryParamMatcher(node) as user =>
        analysisManager.histogram(user, analysisId, node)

      case GET -> Root / UUIDWrapper(analysisId) / "statistics"
            :? NodeQueryParamMatcher(node) as user =>
        analysisManager.statistics(user, analysisId, node)

      case GET -> Root / UUIDWrapper(analysisId) / IntVar(z) / IntVar(x) / IntVar(
            y)
            :? NodeQueryParamMatcher(node) as user =>
        analysisManager.tile(user, analysisId, node, z, x, y)

      case authedReq @ GET -> Root / UUIDWrapper(analysisId) / "raw"
            :? ExtentQueryParamMatcher(extent)
            :? ZoomQueryParamMatcher(zoom)
            :? NodeQueryParamMatcher(node) as user =>
        analysisManager.export(authedReq, user, analysisId, node, extent, zoom)
    }

}
