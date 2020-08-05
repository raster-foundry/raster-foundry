package com.rasterfoundry.datamodel

import com.azavea.stac4s.StacLicense
import io.circe._
import io.circe.generic.semiauto._

final case class StacExportLicense(license: StacLicense, url: Option[String])

object StacExportLicense {
  implicit val codecStacExportLicense: Encoder[StacExportLicense] =
    deriveEncoder[StacExportLicense]
  implicit val decoderStacExportLicense: Decoder[StacExportLicense] =
    deriveDecoder[StacExportLicense]
}
