package com.rasterfoundry.datamodel.stac

import com.rasterfoundry.datamodel._

import io.circe.generic.JsonCodec

import java.sql.Timestamp

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
