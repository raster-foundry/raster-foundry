package com.azavea.rf.tool

import com.azavea.rf.tool._

import cats.syntax.either._
import io.circe._
import io.circe.syntax._
import io.circe.generic.semiauto._
import io.circe.parser.decode
import org.scalatest._
import geotrellis.raster.render._

final case class RenderDefinition(
    breakpoints: Map[Double, RGBA],
    scale: ScaleOpt,
    clip: ClippingOpt
)

trait ScaleOpt { def repr: String }
case object Continuous extends ScaleOpt { def repr = "Continuous" }
case object Sequential extends ScaleOpt { def repr = "Sequential" }
case object Diverging extends ScaleOpt { def repr = "Diverging" }
final case class Qualitative(fallback: RGBA = RGBA(0, 0, 0, 0))
    extends ScaleOpt {
  def repr = {
    val hex = fallback.asJson.noSpaces.stripPrefix("\"").stripSuffix("\"")
    s"Qualitative[$hex]"
  }
}

trait ClippingOpt { def repr: String }
case object ClipNone extends ClippingOpt { def repr = "none" }
case object ClipLeft extends ClippingOpt { def repr = "left" }
case object ClipRight extends ClippingOpt { def repr = "right" }
case object ClipBoth extends ClippingOpt { def repr = "both" }

object RenderDefinition {
  implicit val renderDefinitionDecoder: Decoder[RenderDefinition] =
    deriveDecoder[RenderDefinition]
  implicit val renderDefinitionEncoder: Encoder[RenderDefinition] =
    deriveEncoder[RenderDefinition]
}

object ScaleOpt {
  implicit val scaleOptEncoder: Encoder[ScaleOpt] =
    Encoder.encodeString.contramap[ScaleOpt](_.repr)
  implicit val scaleOptDecoder: Decoder[ScaleOpt] = Decoder.decodeString.emap {
    str =>
      str.toLowerCase match {
        case "sequential" => Right(Sequential)
        case "diverging"  => Right(Diverging)
        case "continuous" => Right(Continuous)
        case qual if qual.startsWith("qualitative[") =>
          val colorString = qual.stripPrefix("qualitative[").stripSuffix("]")
          decode[RGBA](colorString) match {
            case Right(rgba) => Right(Qualitative(rgba))
            case Left(_)     => Left(s"Unable to parse $colorString as RGBA")
          }
        case x => Left(s"Unable to decode $x as a ClippingOption")
      }
  }
}

object ClippingOpt {
  implicit val clipOptEncoder: Encoder[ClippingOpt] =
    Encoder.encodeString.contramap[ClippingOpt](_.repr)
  implicit val clipOptDecoder: Decoder[ClippingOpt] =
    Decoder.decodeString.emap { str =>
      str.toLowerCase match {
        case "none"  => Right(ClipNone)
        case "left"  => Right(ClipLeft)
        case "right" => Right(ClipRight)
        case "both"  => Right(ClipBoth)
        case x       => Left(s"Unable to decode $x as a ClippingOption")
      }
    }
}
