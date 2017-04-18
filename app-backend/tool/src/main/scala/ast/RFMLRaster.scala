package com.azavea.rf.tool.ast

import io.circe._
import io.circe.syntax._
import java.util.UUID
import java.security.InvalidParameterException

import io.circe.generic.JsonCodec

sealed abstract class RFMLRaster(val `type`: String) {
  def id: UUID
}

@JsonCodec
case class SceneRaster(id: UUID, band: Option[Int]) extends RFMLRaster("scene")

@JsonCodec
case class ProjectRaster(id: UUID, band: Option[Int]) extends RFMLRaster("project")

@JsonCodec
case class MLToolRaster(id: UUID, node: Option[UUID]) extends RFMLRaster("tool")

@JsonCodec
case class RasterReference(id: UUID) extends RFMLRaster("reference")

object RFMLRaster {
  implicit lazy val decodeRFMLRaster = Decoder.instance[RFMLRaster] { rasterSrc =>
    rasterSrc._type match {
      case Some("scene") => rasterSrc.as[SceneRaster]
      case Some("project") => rasterSrc.as[ProjectRaster]
      case Some("tool") => rasterSrc.as[MLToolRaster]
      case Some("reference") => rasterSrc.as[RasterReference]
      case Some(unrecognized) =>
        throw new InvalidParameterException(s"'$unrecognized' is not a recognized map algebra raster source type")
      case None =>
        throw new InvalidParameterException(s"Required 'type' property not found on map algebra raster source")
    }
  }

  implicit lazy val encodeRFMLRaster = new Encoder[RFMLRaster] {
    def apply(rasterDefinition: RFMLRaster): Json = rasterDefinition match {
      case scene: SceneRaster => scene.asJson
      case project: ProjectRaster => project.asJson
      case mltool: MLToolRaster => mltool.asJson
      case reference: RasterReference => reference.asJson
    }
  }

  implicit lazy val encodeSceneRaster: Encoder[SceneRaster] =
    Encoder.forProduct3("id", "band", "type")(rfml => (rfml.id, rfml.band, rfml.`type`))

  implicit lazy val encodeProjectRaster: Encoder[ProjectRaster] =
    Encoder.forProduct3("id", "band", "type")(rfml => (rfml.id, rfml.band, rfml.`type`))

  implicit lazy val encodeMLToolRaster: Encoder[MLToolRaster] =
    Encoder.forProduct3("id", "node", "type")(rfml => (rfml.id, rfml.node, rfml.`type`))

  implicit lazy val encodeRasterReference: Encoder[RasterReference] =
    Encoder.forProduct2("id", "type")(rfml => (rfml.id, rfml.`type`))
}
