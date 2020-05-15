package com.rasterfoundry.datamodel

import cats.syntax.either._
import io.circe._

sealed abstract class Continent(val repr: String) {
  override def toString = repr
}

object Continent {
  case object Asia extends Continent("ASIA")
  case object Africa extends Continent("AFRICA")
  case object Antarctica extends Continent("ANTARCTICA")
  case object Australia extends Continent("AUSTRALIA")
  case object Europe extends Continent("EUROPE")
  case object NorthAmerica extends Continent("NORTH_AMERICA")
  case object SouthAmerica extends Continent("SOUTH_AMERICA")

  def fromString(s: String): Continent = s.toUpperCase match {
    case "ASIA"          => Asia
    case "AFRICA"        => Africa
    case "ANTARCTICA"    => Antarctica
    case "AUSTRALIA"     => Australia
    case "EUROPE"        => Europe
    case "NORTH_AMERICA" => NorthAmerica
    case "SOUTH_AMERICA" => SouthAmerica
    case _               => throw new Exception(s"Invalid Continent: ${s}")
  }

  implicit val encContinent: Encoder[Continent] =
    Encoder.encodeString.contramap[Continent](_.toString)

  implicit val decContinent: Decoder[Continent] =
    Decoder.decodeString.emap { s =>
      Either.catchNonFatal(fromString(s)).leftMap(_ => "Continent")
    }
}
