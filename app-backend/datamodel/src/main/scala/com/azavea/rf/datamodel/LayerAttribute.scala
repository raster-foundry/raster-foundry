package com.azavea.rf.datamodel

import geotrellis.spark.LayerId
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
