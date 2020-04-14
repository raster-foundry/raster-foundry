package com.rasterfoundry.common

import com.rasterfoundry.JsonCodecs

import geotrellis.raster.CellType
import io.circe.{Decoder, Encoder}

package object export extends JsonCodecs {

  implicit lazy val celltypeDecoder: Decoder[CellType] =
    Decoder[String].map({ CellType.fromName(_) })
  implicit lazy val celltypeEncoder: Encoder[CellType] =
    Encoder.encodeString.contramap[CellType]({ CellType.toName(_) })

}
