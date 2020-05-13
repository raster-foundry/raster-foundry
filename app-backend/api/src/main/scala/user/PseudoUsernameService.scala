package com.rasterfoundry.api.user

import com.rasterfoundry.datamodel.PseudoUsernameType

import com.github.javafaker._

import java.util.UUID

object PseudoUsernameService {

  def createPseudoName(peudoUserNameType: PseudoUsernameType): String = {
    val uuidSegments = UUID.randomUUID
      .toString()
      .split("-")
      .toIndexedSeq
    val uuidSegOne = uuidSegments(1)
    val uuidSegTwo = uuidSegments(2)
    val faker = new Faker();
    (peudoUserNameType match {
      case PseudoUsernameType.GameOfThrones =>
        s"${faker.gameOfThrones().character()} ${uuidSegOne} ${uuidSegTwo}"
      case PseudoUsernameType.HarryPotter =>
        s"${faker.harryPotter().character()} ${uuidSegOne} ${uuidSegTwo}"
      case PseudoUsernameType.Hobbit =>
        s"${faker.hobbit().character()} ${uuidSegOne} ${uuidSegTwo}"
      case PseudoUsernameType.LordOfTheRings =>
        s"${faker.lordOfTheRings().character()} ${uuidSegOne} ${uuidSegTwo}"
      case PseudoUsernameType.Pokemon =>
        s"${faker.pokemon().name()} ${uuidSegOne} ${uuidSegTwo}"
      case PseudoUsernameType.RickAndMorty =>
        s"${faker.rickAndMorty().character()} ${uuidSegOne} ${uuidSegTwo}"
      case PseudoUsernameType.SuperHero =>
        s"${faker.superhero().prefix()} ${faker
          .superhero()
          .descriptor()} ${uuidSegOne} ${uuidSegTwo}"
    }).replaceAll("\\s", "-")
  }

  def createPseudoNames(
      count: Int,
      peudoUserNameType: PseudoUsernameType
  ): List[String] =
    List.fill(count)(createPseudoName(peudoUserNameType))
}
