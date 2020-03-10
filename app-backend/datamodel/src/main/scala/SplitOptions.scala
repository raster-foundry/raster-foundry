package com.rasterfoundry.datamodel

import cats.syntax.either._
import io.circe._
import io.circe.generic.JsonCodec

import java.sql.Timestamp

sealed abstract class SplitPeriod(val repr: String) {
  override def toString = repr
}

object SplitPeriod {
  case object Day extends SplitPeriod("DAY")
  case object Week extends SplitPeriod("WEEK")

  def fromString(s: String): SplitPeriod = s.toUpperCase match {
    case "DAY"  => Day
    case "WEEK" => Week
    case _      => throw new Exception(s"Invalid string: $s")
  }

  implicit val splitPeriodEncoder: Encoder[SplitPeriod] =
    Encoder.encodeString.contramap[SplitPeriod](_.toString)

  implicit val splitPeriodDecoder: Decoder[SplitPeriod] =
    Decoder.decodeString.emap { str =>
      Either.catchNonFatal(fromString(str)).leftMap(_ => "SplitPeriod")
    }
}

/*
 * Current methods of splitting:
 *  - By period
 *  - By datasource
 * Possible future splits:
 *  - Geometry (one group per polygon in a multipolygon or feature in featureGroup)
 */
@JsonCodec
final case class SplitOptions(
    name: String,
    colorGroupHex: Option[String],
    rangeStart: Timestamp,
    rangeEnd: Timestamp,
    period: SplitPeriod,
    splitOnDatasource: Option[Boolean] = Some(true),
    removeFromLayer: Option[Boolean] = Some(false)
)
