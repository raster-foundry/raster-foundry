package com.rasterfoundry.backsplash.server

import com.rasterfoundry.common.datamodel.User
import com.rasterfoundry.backsplash.error._
import com.rasterfoundry.backsplash.Parameters._

import cats.effect.IO
import org.http4s._
import org.http4s.dsl.io._

class AnalysisService[Param, HistStore](
    algebra: AnalysisAlgebra[Param, HistStore])(
    implicit H: HttpErrorHandler[IO, BacksplashException, User],
    ForeignError: HttpErrorHandler[IO, Throwable, User]) {

  val routes: AuthedService[User, IO] = H.handle {
    ForeignError.handle {
      AuthedService {
        case GET -> Root / UUIDWrapper(analysisId) / "histogram"
              :? NodeQueryParamMatcher(node) as user =>
          algebra.histogram(user, analysisId, node)

        case GET -> Root / UUIDWrapper(analysisId) / "statistics"
              :? NodeQueryParamMatcher(node) as user =>
          algebra.statistics(user, analysisId, node)

        case GET -> Root / UUIDWrapper(analysisId) / IntVar(z) / IntVar(x) / IntVar(
              y)
              :? NodeQueryParamMatcher(node) as user =>
          algebra.tile(user, analysisId, node, z, x, y)

        case authedReq @ GET -> Root / UUIDWrapper(analysisId) / "raw"
              :? ExtentQueryParamMatcher(extent)
              :? ZoomQueryParamMatcher(zoom)
              :? NodeQueryParamMatcher(node) as user =>
          algebra.export(authedReq, user, analysisId, node, extent, zoom)
      }
    }
  }

}
