package com.azavea.rf.tool.ast

import io.circe._
import io.circe.generic.auto._

import java.util.UUID
import java.security.InvalidParameterException


sealed abstract class RFMLRaster(val `type`: String) {
  def id: UUID
}
case class SceneRaster(id: UUID, band: Option[Int]) extends RFMLRaster("scene")
case class ProjectRaster(id: UUID, band: Option[Int]) extends RFMLRaster("project")
case class MLToolRaster(id: UUID, node: Option[UUID]) extends RFMLRaster("tool")
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
}
