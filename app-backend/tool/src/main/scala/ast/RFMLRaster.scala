package com.azavea.rf.tool.ast

import java.security.InvalidParameterException
import java.util.UUID

import io.circe._
import io.circe.generic.JsonCodec
import io.circe.syntax._

sealed abstract class RFMLRaster(val `type`: String) {
  def id: UUID
}

case class SceneRaster(id: UUID, band: Option[Int]) extends RFMLRaster("scene")

case class ProjectRaster(id: UUID, band: Option[Int]) extends RFMLRaster("project")

object RFMLRaster {
  implicit lazy val decodeRFMLRaster = Decoder.instance[RFMLRaster] { rasterSrc =>
    rasterSrc._type match {
      case Some("scene") => rasterSrc.as[SceneRaster]
      case Some("project") => rasterSrc.as[ProjectRaster]
      case Some(unrecognized) =>
        throw new InvalidParameterException(s"'$unrecognized' is not a recognized map algebra raster source type")
      case None =>
        throw new InvalidParameterException("Required property, 'type', not found on map algebra raster source")
    }
  }

  implicit lazy val decodeSceneRaster: Decoder[SceneRaster] =
    Decoder.forProduct2("id", "band")(SceneRaster.apply)
  implicit lazy val encodeSceneRaster: Encoder[SceneRaster] =
    Encoder.forProduct3("id", "band", "type")(op => (op.id, op.band, op.`type`))

  implicit lazy val decodeProjectRaster: Decoder[ProjectRaster] =
    Decoder.forProduct2("id", "band")(ProjectRaster.apply)
  implicit lazy val encodeProjectRaster: Encoder[ProjectRaster] =
    Encoder.forProduct3("id", "band", "type")(op => (op.id, op.band, op.`type`))

  implicit lazy val encodeRFMLRaster = new Encoder[RFMLRaster] {
    def apply(rasterDefinition: RFMLRaster): Json = rasterDefinition match {
      case scene: SceneRaster => scene.asJson
      case project: ProjectRaster => project.asJson
    }
  }
}
