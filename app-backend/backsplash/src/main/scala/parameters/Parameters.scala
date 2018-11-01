package com.rasterfoundry.backsplash.parameters

import com.rasterfoundry.backsplash.nodes.ProjectNode
import geotrellis.vector.Extent
import org.http4s._
import org.http4s.dsl.io._

import scala.util.Try

import java.util.UUID

object Parameters {
  implicit val extentQueryParamDecoder: QueryParamDecoder[Extent] =
    QueryParamDecoder[String].map(Extent.fromString)

  object RedBandOptionalQueryParamMatcher
      extends OptionalQueryParamDecoderMatcher[Int]("redBand")
  object GreenBandOptionalQueryParamMatcher
      extends OptionalQueryParamDecoderMatcher[Int]("greenBand")
  object BlueBandOptionalQueryParamMatcher
      extends OptionalQueryParamDecoderMatcher[Int]("blueBand")
  object ExtentQueryParamMatcher
      extends QueryParamDecoderMatcher[Extent]("bbox")
  object NodeQueryParamMatcher extends QueryParamDecoderMatcher[String]("node")
  object TagOptionalQueryParamMatcher
      extends OptionalQueryParamDecoderMatcher[String]("tag")
  object ZoomQueryParamMatcher extends QueryParamDecoderMatcher[Int]("zoom")

  object UUIDWrapper {
    def unapply(s: String): Option[UUID] = {
      if (!s.isEmpty) Try(UUID.fromString(s)).toOption else None
    }
  }
}
