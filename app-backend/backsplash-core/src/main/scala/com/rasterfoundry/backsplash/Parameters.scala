package com.rasterfoundry.backsplash

import geotrellis.vector.Extent
import org.http4s._
import org.http4s.dsl.io._

import scala.util.Try

import java.util.UUID

object Parameters {

  /** Query param decoders */
  implicit val extentQueryParamDecoder: QueryParamDecoder[Extent] =
    QueryParamDecoder[String].map(Extent.fromString)
  implicit val uuidQueryParamDecoder: QueryParamDecoder[UUID] =
    QueryParamDecoder[String].map(UUID.fromString)

  /** Query string query parameters */
  object TokenQueryParamMatcher
      extends OptionalQueryParamDecoderMatcher[String]("token")
  object MapTokenQueryParamMatcher
      extends OptionalQueryParamDecoderMatcher[UUID]("mapToken")
  object RedBandOptionalQueryParamMatcher
      extends OptionalQueryParamDecoderMatcher[Int]("redBand")
  object GreenBandOptionalQueryParamMatcher
      extends OptionalQueryParamDecoderMatcher[Int]("greenBand")
  object BlueBandOptionalQueryParamMatcher
      extends OptionalQueryParamDecoderMatcher[Int]("blueBand")
  object ExtentQueryParamMatcher
      extends QueryParamDecoderMatcher[Extent]("bbox")
  object NodeQueryParamMatcher
      extends OptionalQueryParamDecoderMatcher[UUID]("node")
  object TagOptionalQueryParamMatcher
      extends OptionalQueryParamDecoderMatcher[String]("tag")
  object VoidCacheQueryParamMatcher
      extends QueryParamDecoderMatcher[Boolean]("voidCache")
  object ZoomQueryParamMatcher extends QueryParamDecoderMatcher[Int]("zoom")

  /** Path Parameters */
  object UUIDWrapper {
    def unapply(s: String): Option[UUID] = {
      if (!s.isEmpty) Try(UUID.fromString(s)).toOption else None
    }
  }
}
