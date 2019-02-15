package com.rasterfoundry.backsplash

import com.rasterfoundry.common.datamodel.BandOverride

import cats.implicits._
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

  object BandOverrideQueryParamDecoder {

    /** This returns a "Some[Option[]] so that it will "always match".
      * See Path.scala in http4s
      */
    def unapply(
        params: Map[String, Seq[String]]): Some[Option[BandOverride]] = {
      Some {
        (
          params.get("redBand") flatMap { _.headOption } map {
            Integer.parseInt(_)
          },
          params.get("greenBand") flatMap { _.headOption } map {
            Integer.parseInt(_)
          },
          params.get("blueBand") flatMap { _.headOption } map {
            Integer.parseInt(_)
          }
        ).tupled map {
          // We have to wrap the option in another option to get the query param matcher
          // to emit the correct type
          case (r, g, b) => BandOverride(r, g, b)
        }
      }
    }
  }

  /** Query string query parameters */
  object TokenQueryParamMatcher
      extends OptionalQueryParamDecoderMatcher[String]("token")
  object MapTokenQueryParamMatcher
      extends OptionalQueryParamDecoderMatcher[UUID]("mapToken")
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
