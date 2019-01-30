package com.rasterfoundry.common.datamodel.stac

import com.rasterfoundry.common.datamodel._

import geotrellis.vector.{Geometry, Projected}
import io.circe._
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
  Encoder[Properties]
  Encoder[Projected[Geometry]]
  Encoder[Link]
  Encoder[Asset]
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
