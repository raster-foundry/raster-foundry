package com.azavea.rf

import cats.syntax.either._
import io.circe._
import io.circe.syntax._
import io.circe.generic.semiauto._
import io.circe.parser.decode
import org.scalatest._
import geotrellis.raster.render._

import scala.util.Try

package object tool {
  // Double key serialization
  implicit val decodeKeyDouble: KeyDecoder[Double] = new KeyDecoder[Double] {
    final def apply(key: String): Option[Double] = Try(key.toDouble).toOption
  }
  implicit val encodeKeyDouble: KeyEncoder[Double] = new KeyEncoder[Double] {
    final def apply(key: Double): String = key.toString
  }

  // RGBA deserialization
  implicit val decodeHexRGBA: Decoder[RGBA] = Decoder.decodeString.emap { str =>
    str.stripPrefix("#").stripPrefix("0x") match {
      case hex if (hex.size == 8) =>
        val bytes = hex
          .sliding(2, 2)
          .map({ hexByte =>
            Integer.parseInt(hexByte, 16)
          })
          .toList
        Right(RGBA(bytes(0), bytes(1), bytes(2), bytes(3)))
      case hex if (hex.size == 6) =>
        val bytes = hex
          .sliding(2, 2)
          .map({ hexByte =>
            Integer.parseInt(hexByte, 16)
          })
          .toList
        Right(RGB(bytes(0), bytes(1), bytes(2)))
      case hex => Left(s"Unable to parse $hex as an RGBA")
    }
  }
  implicit val encodeRgbaAsHex: Encoder[RGBA] =
    Encoder.encodeString.contramap[RGBA] { rgba =>
      "#" + rgba.red.toHexString + rgba.blue.toHexString + rgba.green.toHexString + rgba.alpha.toHexString
    }
}
