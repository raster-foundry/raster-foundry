package com.azavea.rf.datamodel.stac

import com.azavea.rf.bridge._
import geotrellis.vector.Geometry
import geotrellis.slick.Projected

import io.circe._
import io.circe.generic.JsonCodec

@JsonCodec
case class Feature(
  `type`: String = "Feature",
  id: String,
  properties: Properties,
  geometry: Projected[Geometry],
  bbox: List[Double],
  links: Seq[Link],
  assets: Seq[Asset]
)

object Feature {
  def validate(feature: Feature): Either[String, Feature] = {
    (feature.`type` == "Feature") match {
      case true =>
        feature.links.map(Link.validate(_))
          .partition(_.isLeft) match {
            case (Nil, _) =>
              Right(feature)
          case (strings, _) => Left(strings.mkString(", "))
        }
      case _ =>
        Left(s"Invalid feature type: ${feature.`type`}")
    }
  }
}
