package com.azavea.rf.datamodel.stac

import java.sql.Timestamp

import com.azavea.rf.bridge._
import geotrellis.vector.Geometry
import geotrellis.slick.Projected

import io.circe._
import io.circe.generic.JsonCodec

@JsonCodec
case class Properties(
  license: String,
  provider: String,
  start: Timestamp,
  end: Timestamp
)

object Properties {
  def validate(properties: Properties): Either[String, Properties] = {
    Right(properties)
  }
}
