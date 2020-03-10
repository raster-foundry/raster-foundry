package com.rasterfoundry.datamodel

import cats.syntax.either._
import io.circe._

sealed abstract class SceneType(val repr: String) extends Serializable {
  override def toString = repr
}

object SceneType {
  case object Avro extends SceneType("AVRO")
  case object COG extends SceneType("COG")

  def fromString(s: String): SceneType = {
    s.toUpperCase match {
      case "AVRO" => SceneType.Avro
      case "COG"  => SceneType.COG
      case _      => throw new Exception(s"Invalid SceneType string: $s")
    }
  }

  implicit val jobStatusEncoder: Encoder[SceneType] =
    Encoder.encodeString.contramap[SceneType](_.toString)

  implicit val jobStatusDecoder: Decoder[SceneType] =
    Decoder.decodeString.emap { str =>
      Either.catchNonFatal(fromString(str)).leftMap(_ => "SceneType")
    }
}
