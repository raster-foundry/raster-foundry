package com.azavea.rf.datamodel.stac

import java.sql.Timestamp

import io.circe.generic.JsonCodec

@JsonCodec
final case class Properties(license: String,
                            provider: String,
                            start: Timestamp,
                            end: Timestamp)

object Properties {
  def validate(properties: Properties): Either[String, Properties] = {
    Right(properties)
  }
}
