package com.rasterfoundry.datamodel

import io.circe.generic.JsonCodec
import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder}

import scala.util.Try

import java.util.UUID

@JsonCodec
final case class Band(id: UUID,
                      image: UUID,
                      name: String,
                      number: Int,
                      wavelength: List[Int])

object Band {
  def create = Create.apply _

  def tupled = (Band.apply _).tupled

  final case class Create(name: String, number: Int, wavelength: List[Int]) {

    def toBand(imageId: UUID): Band = {
      Band(UUID.randomUUID, imageId, name, number, wavelength)
    }
  }

  object Create {
    implicit val encodeCreate: Encoder[Create] = deriveEncoder[Create]
    // The second decoder exists because we've done some truly bizarre json things
    // in the db, at least from the perspective of whether the things in the db should
    // ever be translatable back into bands.
    // Note that because of Sentinel-2's 8A band, band numbers for all bands above that number
    // will be wrong for Sentinel-2 scenes.
    implicit val decodeCreate
      : Decoder[Create] = deriveDecoder[Create] or Decoder.forProduct3(
      "name",
      "number",
      "wavelength")((name: String, number: String, wavelength: Float) => {
      Create(name,
             (Try { number.toInt } toOption) getOrElse { 0 },
             List(wavelength.toInt))
    })
  }

  @JsonCodec
  final case class Identified(id: Option[UUID],
                              imageId: UUID,
                              name: String,
                              number: Int,
                              wavelength: List[Int]) {
    def toBand: Band = {
      Band(id.getOrElse(UUID.randomUUID), imageId, name, number, wavelength)
    }
  }

  object Identified
}
