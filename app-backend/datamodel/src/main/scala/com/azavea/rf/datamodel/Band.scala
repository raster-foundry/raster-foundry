package com.azavea.rf.datamodel

import java.util.UUID

import io.circe.generic.JsonCodec

@JsonCodec
final case class Band(id: UUID,
                      image: UUID,
                      name: String,
                      number: Int,
                      wavelength: List[Int])

object Band {
  def create = Create.apply _

  def tupled = (Band.apply _).tupled

  @JsonCodec
  final case class Create(name: String, number: Int, wavelength: List[Int]) {

    def toBand(imageId: UUID): Band = {
      Band(UUID.randomUUID, imageId, name, number, wavelength)
    }
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
