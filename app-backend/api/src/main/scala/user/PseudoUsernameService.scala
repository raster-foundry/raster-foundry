package com.rasterfoundry.api.user

import com.rasterfoundry.datamodel.PseudoUsernameType

import scala.util.Random

import java.util.UUID
import com.github.javafaker._

object PseudoUsernameService {

  def createPseudoName(peudoUserNameType: PseudoUsernameType): String = {
    val uuidSeg =
      UUID.randomUUID.toString().split("-").toList(1 + (new Random).nextInt(3))
    val faker = new Faker();
    (peudoUserNameType match {
      case PseudoUsernameType.GameOfThrones =>
        s"${faker.gameOfThrones().character()} at ${faker.gameOfThrones().house()} ${uuidSeg}"
      case PseudoUsernameType.HarryPotter =>
        s"${faker.harryPotter().character()} at ${faker.harryPotter().location()} ${uuidSeg}"
      case PseudoUsernameType.Hobbit =>
        s"${faker.hobbit().character()} at ${faker.hobbit().location()} ${uuidSeg}"
      case PseudoUsernameType.LordOfTheRings =>
        s"${faker.lordOfTheRings().character()} at ${faker.lordOfTheRings().location()} ${uuidSeg}"
      case PseudoUsernameType.Pokemon =>
        s"${faker.pokemon().name()} at ${faker.pokemon().location()} ${uuidSeg}"
      case PseudoUsernameType.RickAndMorty =>
        s"${faker.rickAndMorty().character()} at ${faker.rickAndMorty().location()} ${uuidSeg}"
      case PseudoUsernameType.SuperHero =>
        s"${faker.superhero().prefix()} ${faker
          .superhero()
          .descriptor()} ${faker.superhero().suffix()} ${uuidSeg}"
    }).replaceAll("\\s", "-")
  }

  def createPseudoNames(
      count: Int,
      peudoUserNameType: PseudoUsernameType
  ): List[String] =
    List.fill(count)(createPseudoName(peudoUserNameType))
}
