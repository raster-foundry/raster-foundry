package com.azavea.rf.datamodel.stac

import java.util.UUID

import io.circe._
import io.circe.generic.JsonCodec

@JsonCodec
case class Asset (
  href: String,
  name: Option[String] ,
  product: Option[String],
  format: Option[String]
)

object Asset {
  def validate(asset: Asset): Either[String, Asset] = {
    if (asset.href.length < 1) {
      Left(s"Invalid asset - href must be a string with length > 0; ${asset}")
    } else {
      Right(asset)
    }
  }
}
