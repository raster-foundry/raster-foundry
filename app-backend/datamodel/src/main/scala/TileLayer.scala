package com.rasterfoundry.datamodel

import io.circe.generic.semiauto._
import io.circe._

import java.net.URI
import java.util.UUID

final case class TileLayer(
    id: UUID,
    name: String,
    url: URI,
    default: Boolean,
    overlay: Boolean,
    layerType: TileLayerType,
    annotationProjectId: UUID
)

object TileLayer {
  implicit val encTileLayer: Encoder[TileLayer] = deriveEncoder
  implicit val decTileLayer: Decoder[TileLayer] = deriveDecoder

  final case class Create(
      name: String,
      url: URI,
      default: Option[Boolean],
      overlay: Option[Boolean],
      layerType: TileLayerType
  )

  object Create {
    implicit val decTileLayerCreate: Decoder[Create] = deriveDecoder
  }
}
