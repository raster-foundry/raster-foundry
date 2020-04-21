package com.rasterfoundry.common.export

import _root_.io.circe.generic.semiauto._
import geotrellis.vector.io.json.Implicits._
import geotrellis.proj4.CRS

final case class OutputDefinition(
    crs: Option[CRS],
    destination: String,
    dropboxCredential: Option[String]
)

object OutputDefinition {
  implicit def encodeOutputDefinition = deriveEncoder[OutputDefinition]
  implicit def decodeOutputDefinition = deriveDecoder[OutputDefinition]
}
