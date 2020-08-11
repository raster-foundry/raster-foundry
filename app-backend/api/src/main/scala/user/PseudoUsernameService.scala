package com.rasterfoundry.api.user

import com.rasterfoundry.datamodel.PseudoUsernameType

import com.github.javafaker._

import scala.util.Random

import java.util.UUID

object PseudoUsernameService {

  private val moonsOfJupiter = List(
    "Adrastea",
    "Aitne",
    "Amalthea",
    "Ananke",
    "Aoede",
    "Arche",
    "Autonoe",
    "Callirrhoe",
    "Callisto",
    "Carme",
    "Carpo",
    "Chaldene",
    "Cyllene",
    "Dia",
    "Elara",
    "Erinome",
    "Eukelade",
    "Euanthe",
    "Euporie",
    "Europa",
    "Eurydome",
    "Ganymede",
    "Harpalyke",
    "Hegemone",
    "Helike",
    "Hermippe",
    "Herse",
    "Himalia",
    "Io",
    "Iocaste",
    "Isonoe",
    "Kale",
    "Kallichore",
    "Kalyke",
    "Kore",
    "Leda",
    "Lysithea",
    "Mneme",
    "Orthosie",
    "Pasiphae",
    "Pasithee",
    "Praxidike",
    "Sinope",
    "Sponde",
    "Thebe",
    "Themisto",
    "Taygete",
    "Thelxinoe",
    "Thyone",
    "S/2003 J2",
    "S/2003 J3",
    "S/2003 J4",
    "S/2003 J5",
    "S/2003 J9",
    "S/2003 J10",
    "S/2003 J12",
    "S/2003 J15",
    "S/2003 J16",
    "S/2003 J18",
    "S/2003 J19",
    "S/2003 J23",
    "S/2011 J1",
    "S/2011 J2"
  )

  private val animalAstronauts = List(
    "Albert",
    "Yorick",
    "Patricia",
    "Mike",
    "Gordo",
    "Able",
    "Baker",
    "Sam",
    "Miss Sam",
    "Ham",
    "Goliath",
    "Enos",
    "Bonnie",
    "Abrek",
    "Bion",
    "Verny",
    "Gordy",
    "Yerosha",
    "Dryoma",
    "Zhakonya",
    "Zabiyaka",
    "Krosh",
    "Ivasha",
    "Lapik",
    "Multik",
    "Dezik",
    "Lisa",
    "Smelaya",
    "Malyshka",
    "ZIB",
    "Albina",
    "Dymka",
    "Modnista",
    "Kozyavka",
    "Laika",
    "Bars",
    "Lisichka",
    "Belka",
    "Strelka",
    "Pchelka",
    "Muska",
    "Damka",
    "Krasavka",
    "Zvezdochka",
    "Veterok",
    "Mildred",
    "Albert",
    "Laska",
    "Wilkie",
    "Felicette",
    "Anita",
    "Arabella"
  )

  private val earthNames = List(
    "Terra",
    "Tierra",
    "Terre",
    "Pamant",
    "Tiere",
    "Tiara",
    "Terrinu",
    "Jord",
    "Aarde",
    "Erde",
    "Earth",
    "Erd",
    "Talamh",
    "Ierde",
    "Buedem",
    "Zemlja",
    "Ziemia",
    "Ziamli",
    "Zeme",
    "Domhain",
    "Talamh",
    "Cre",
    "Tir",
    "Daeaer",
    "Douar",
    "Yeryuzu",
    "Ge",
    "Maa",
    "Toke",
    "Yerkir",
    "Yerger",
    "Kedin",
    "Erkir",
    "Zamin",
    "Bhim",
    "Palal",
    "Mati",
    "Mitti",
    "Prithbi",
    "Zamin",
    "Metsu",
    "Xak",
    "Dunia",
    "Ile aye",
    "Duniya",
    "Uwa",
    "Umhlaba",
    "Pasi",
    "Midiri",
    "Ard",
    "Eretz",
    "Lafa",
    "Diqiu",
    "Kambharmyay",
    "Prthvi",
    "Jigu",
    "Chikyu",
    "Trai dat",
    "Phendei",
    "Lok",
    "Aephndin olk",
    "Vanua",
    "Yuta",
    "Lupa",
    "Bumi",
    "Pumi"
  )

  def createPseudoName(peudoUserNameType: PseudoUsernameType): String = {
    val uuidSegments = UUID.randomUUID
      .toString()
      .split("-")
      .toIndexedSeq
    val uuidSegOne = uuidSegments(1)
    val uuidSegTwo = uuidSegments(2)
    val faker = new Faker()
    val random = new Random

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
      case PseudoUsernameType.StarTrek =>
        s"${faker.starTrek().character()} ${uuidSegOne} ${uuidSegTwo}"
      case PseudoUsernameType.SuperHero =>
        s"${faker.superhero().prefix()} ${faker
          .superhero()
          .descriptor()} ${uuidSegOne} ${uuidSegTwo}"
      case PseudoUsernameType.AnimalAstronauts =>
        s"${animalAstronauts(random.nextInt(animalAstronauts.length))} ${uuidSegOne} ${uuidSegTwo}"
      case PseudoUsernameType.MoonsOfJupiter =>
        s"${moonsOfJupiter(random.nextInt(moonsOfJupiter.length))} ${uuidSegOne} ${uuidSegTwo}"
      case PseudoUsernameType.EarthNames =>
        s"${earthNames(random.nextInt(earthNames.length))} ${uuidSegOne} ${uuidSegTwo}"
    }).replaceAll("\\s", "-").toLowerCase().capitalize
  }

  def createPseudoNames(
      count: Int,
      peudoUserNameType: PseudoUsernameType
  ): List[String] =
    List.fill(count)(createPseudoName(peudoUserNameType))
}
