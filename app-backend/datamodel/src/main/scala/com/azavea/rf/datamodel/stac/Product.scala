package com.azavea.rf.datamodel.stac

import io.circe._
import io.circe.generic.JsonCodec

@JsonCodec
final case class Product(id: String,
                         bands: Seq[Band],
                         filetype: String,
                         origin: String,
                         properties: Json)

object Product {
  def validate(product: Product): Either[String, Product] = {
    Right(product)
  }
}
