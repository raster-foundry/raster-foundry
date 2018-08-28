package com.azavea.rf.datamodel.stac

import io.circe.generic.JsonCodec

@JsonCodec
final case class Band(commonName: String,
                      gsd: Float,
                      centerWavelength: Float,
                      effectiveBandwidth: Float,
                      imageBandIndex: Int)

object Band {
  def validate(band: Band): Either[String, Band] = {
    Right(band)
  }
}
