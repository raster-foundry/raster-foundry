package com.rasterfoundry.common

import geotrellis.store.LayerId
import io.circe._
import io.circe.generic.JsonCodec

@JsonCodec
final case class LayerAttribute(layerName: String,
                                zoom: Int,
                                name: String,
                                value: Json) {
  def layerId: LayerId = LayerId(layerName, zoom)
}

object LayerAttribute {
  def tupled = (LayerAttribute.apply _).tupled

  def create = LayerAttribute.apply _
}
