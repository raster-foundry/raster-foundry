package com.azavea.rf.datamodel.stac

import geotrellis.vector.{Geometry, Projected}
import io.circe.generic.JsonCodec

@JsonCodec
final case class Feature(`type`: String = "Feature",
                         id: String,
                         properties: Properties,
                         geometry: Projected[Geometry],
                         bbox: List[Double],
                         links: Seq[Link],
                         assets: Seq[Asset])

object Feature {
  def validate(feature: Feature): Either[String, Feature] = {
    if (feature.`type` == "Feature") {
      feature.links
        .map(Link.validate)
        .partition(_.isLeft) match {
        case (Nil, _) =>
          Right(feature)
        case (strings, _) => Left(strings.mkString(", "))
      }
    } else {
      Left(s"Invalid feature type: ${feature.`type`}")
    }
  }
}
