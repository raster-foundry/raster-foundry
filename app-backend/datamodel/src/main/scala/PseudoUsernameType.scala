package com.rasterfoundry.datamodel

import cats.syntax.either._
import io.circe._

sealed abstract class PseudoUsernameType(val repr: String) {
  override def toString = repr
}

object PseudoUsernameType {
  case object GameOfThrones extends PseudoUsernameType("GAME_OF_THRONES")
  case object HarryPotter extends PseudoUsernameType("HARRY_POTTER")
  case object Hobbit extends PseudoUsernameType("HOBBIT")
  case object LordOfTheRings extends PseudoUsernameType("LORD_OF_THE_RINGS")
  case object Pokemon extends PseudoUsernameType("POKEMON")
  case object RickAndMorty extends PseudoUsernameType("RICK_AND_MORTY")
  case object StarTrek extends PseudoUsernameType("STAR_TREK")
  case object SuperHero extends PseudoUsernameType("SUPER_HERO")
  case object MoonsOfJupiter extends PseudoUsernameType("MOONS_OF_JUPITER")
  case object AnimalAstronauts extends PseudoUsernameType("ANIMAL_ASTRONAUTS")
  case object EarthNames extends PseudoUsernameType("EARTH_NAMES")

  def fromString(s: String): PseudoUsernameType = s.toUpperCase match {
    case "GAME_OF_THRONES"   => GameOfThrones
    case "HARRY_POTTER"      => HarryPotter
    case "HOBBIT"            => Hobbit
    case "LORD_OF_THE_RINGS" => LordOfTheRings
    case "POKEMON"           => Pokemon
    case "RICK_AND_MORTY"    => RickAndMorty
    case "STAR_TREK"         => StarTrek
    case "SUPER_HERO"        => SuperHero
    case "MOONS_OF_JUPITER"  => MoonsOfJupiter
    case "ANIMAL_ASTRONAUTS" => AnimalAstronauts
    case "EARTH_NAMES"       => EarthNames
    case _                   => throw new Exception(s"Invalid string: $s")
  }

  implicit val annotationProjectTypeEncoder: Encoder[PseudoUsernameType] =
    Encoder.encodeString.contramap[PseudoUsernameType](_.toString)

  implicit val annotationProjectTypeDecoder: Decoder[PseudoUsernameType] =
    Decoder.decodeString.emap { str =>
      Either
        .catchNonFatal(fromString(str))
        .leftMap(_ => "PseudoUsernameType")
    }
}
