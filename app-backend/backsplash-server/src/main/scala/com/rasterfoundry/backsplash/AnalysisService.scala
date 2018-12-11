package com.rasterfoundry.backsplash.server

import com.rasterfoundry.backsplash.Parameters._

import cats.data.Validated._
import cats.effect.{ContextShift, IO}
import geotrellis.raster.render.ColorRamps
import io.circe.syntax._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.headers._
import org.http4s.circe._

import com.rasterfoundry.backsplash._
import com.rasterfoundry.backsplash.Implicits._

class AnalysisService[Param: ToolStore](analyses: Param)(
    implicit cs: ContextShift[IO])
    extends BacksplashMamlAdapter {
  val routes: HttpRoutes[IO] = HttpRoutes.of {

    case GET -> Root / UUIDWrapper(analysisId) / "histogram"
          :? NodeQueryParamMatcher(node)
          :? VoidCacheQueryParamMatcher(void) =>
      for {
        paintable <- analyses.read(analysisId, node)
        histsValidated <- paintable.histogram(4000)
        resp <- histsValidated match {
          case Valid(hists) =>
            Ok(hists.head asJson)
          case Invalid(e) =>
            BadRequest(s"Unable to produce histogram for $analysisId: $e")
        }
      } yield resp

    case GET -> Root / UUIDWrapper(analysisId) / IntVar(z) / IntVar(x) / IntVar(
          y)
          :? NodeQueryParamMatcher(node) =>
      for {
        paintable <- analyses.read(analysisId, node)
        tileValidated <- paintable.tms(z, x, y)
        resp <- tileValidated match {
          case Valid(tile) =>
            Ok(tile.band(0).renderPng(ColorRamps.Viridis).bytes,
               `Content-Type`(MediaType.image.png))
          case Invalid(e) =>
            BadRequest(s"Unable to produce tile for $analysisId: $e")
        }
      } yield resp
  }
}
