package com.rasterfoundry.backsplash

import com.rasterfoundry.datamodel.BandOverride

import cats.Applicative
import cats.implicits._
import geotrellis.vector.Extent
import io.circe.parser._
import org.http4s._
import org.http4s.dsl.io._

import scala.util.Try

import java.util.UUID

object Parameters {

  final case class ThumbnailSize(width: Int, height: Int)

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
        params: Map[String, Seq[String]]
    ): Some[Option[BandOverride]] = {
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

  object ThumbnailQueryParamDecoder {
    def unapply(params: Map[String, Seq[String]]): Option[ThumbnailSize] = {
      val width: Option[Int] =
        params.get("width") flatMap { _.headOption } flatMap {
          decode[Int](_).toOption
        }
      val height: Option[Int] =
        params.get("height") flatMap { _.headOption } flatMap {
          decode[Int](_).toOption
        }
      Applicative[Option].map2(width, height)(ThumbnailSize.apply _) orElse {
        Some(ThumbnailSize(128, 128))
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
  object BrightnessFloorQueryParamMatcher
      extends OptionalQueryParamDecoderMatcher[Int]("floor")

  /** Path Parameters */
  object UUIDWrapper {
    def unapply(s: String): Option[UUID] = {
      if (!s.isEmpty) Try(UUID.fromString(s)).toOption else None
    }
  }
}
