package com.azavea.rf.datamodel

import akka.http.scaladsl.unmarshalling._
import java.util.UUID

import io.circe._
import io.circe.generic.JsonCodec

@JsonCodec
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

  @JsonCodec
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

  @JsonCodec
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

  object Identified
}
