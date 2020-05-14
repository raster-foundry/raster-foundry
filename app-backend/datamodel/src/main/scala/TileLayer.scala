package com.rasterfoundry.datamodel

import io.circe._
import io.circe.generic.semiauto._

import java.util.UUID

final case class TileLayer(
    id: UUID,
    name: String,
    url: String,
    default: Boolean,
    overlay: Boolean,
    layerType: TileLayerType,
    annotationProjectId: UUID,
    quality: Option[TileLayerQuality] = None
)

object TileLayer {
  implicit val encTileLayer: Encoder[TileLayer] = deriveEncoder
  implicit val decTileLayer: Decoder[TileLayer] = deriveDecoder

  final case class Create(
      name: String,
      url: String,
      default: Option[Boolean],
      overlay: Option[Boolean],
      layerType: TileLayerType,
      quality: Option[TileLayerQuality] = None
  )

  object Create {
    implicit val decTileLayerCreate: Decoder[Create] = deriveDecoder
  }
}
