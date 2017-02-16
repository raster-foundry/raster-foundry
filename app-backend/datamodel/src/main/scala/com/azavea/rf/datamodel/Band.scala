package com.azavea.rf.datamodel

import akka.http.scaladsl.unmarshalling._
import spray.json._
import spray.json.DefaultJsonProtocol._
import java.util.UUID

case class Band(
  id: UUID,
  image: UUID,
  name: String,
  number: Int,
  wavelength: List[Int]
)

object Band {//extends RangeUnmarshaler{

  def create = Create.apply _

  def tupled = (Band.apply _).tupled

  implicit val defaultBandFormat = jsonFormat5(Band.apply _)

  case class Create(
    name: String,
    number: Int,
    wavelength: List[Int]
  ) {

    def toBand(imageId: UUID): Band = {
      Band(
        UUID.randomUUID,
        imageId,
        name,
        number,
        wavelength
      )
    }
  }

  object Create {
    implicit val defaultBandCreateFormat = jsonFormat3(Create.apply _)
  }

  case class Identified(
    id: Option[UUID],
    imageId: UUID,
    name: String,
    number: Int,
    wavelength: List[Int]
  ) {
    def toBand: Band = {
      Band(
        id.getOrElse(UUID.randomUUID),
        imageId,
        name,
        number,
        wavelength
      )
    }
  }

  object Identified {
    implicit val defaultIdentifiedBandFormat = jsonFormat5(Identified.apply _)
  }
}
