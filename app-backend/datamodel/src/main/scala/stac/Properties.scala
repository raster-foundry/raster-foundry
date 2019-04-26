package com.rasterfoundry.datamodel.stac

import com.rasterfoundry.datamodel._

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
