package com.rasterfoundry.common.datamodel.export

import geotrellis.proj4.CRS
import _root_.io.circe.generic.semiauto._

final case class OutputDefinition(
    crs: Option[CRS],
    destination: String,
    dropboxCredential: Option[String]
)

object OutputDefinition {
  implicit def encodeOutputDefinition = deriveEncoder[OutputDefinition]
  implicit def decodeOutputDefinition = deriveDecoder[OutputDefinition]
}
