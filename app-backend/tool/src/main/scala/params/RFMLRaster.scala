package com.azavea.rf.tool.params

import com.azavea.rf.tool.ast._
import com.azavea.rf.tool.ast.codec.MapAlgebraUtilityCodecs

import io.circe._
import io.circe.syntax._
import geotrellis.raster.CellType

import java.security.InvalidParameterException
import java.util.UUID


sealed abstract class RFMLRaster(val `type`: String) extends Serializable {
  def id: UUID
}

case class SceneRaster(id: UUID, band: Option[Int], celltype: Option[CellType]) extends RFMLRaster("scene")

case class ProjectRaster(id: UUID, band: Option[Int], celltype: Option[CellType]) extends RFMLRaster("project")

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

  implicit lazy val celltypeDecoder: Decoder[CellType] =
    Decoder[String].map({ CellType.fromName(_) })

  implicit lazy val celltypeEncoder: Encoder[CellType] =
    Encoder.encodeString.contramap[CellType]({ CellType.toName(_) })

  implicit lazy val decodeSceneRaster: Decoder[SceneRaster] =
    Decoder.forProduct3("id", "band", "celltype")(SceneRaster.apply)
  implicit lazy val encodeSceneRaster: Encoder[SceneRaster] =
    Encoder.forProduct4("id", "band", "celltype", "type")(op => (op.id, op.band, op.celltype, op.`type`))

  implicit lazy val decodeProjectRaster: Decoder[ProjectRaster] =
    Decoder.forProduct3("id", "band", "celltype")(ProjectRaster.apply)
  implicit lazy val encodeProjectRaster: Encoder[ProjectRaster] =
    Encoder.forProduct4("id", "band", "celltype", "type")(op => (op.id, op.band, op.celltype, op.`type`))

  implicit lazy val encodeRFMLRaster = new Encoder[RFMLRaster] {
    def apply(rasterDefinition: RFMLRaster): Json = rasterDefinition match {
      case scene: SceneRaster => scene.asJson
      case project: ProjectRaster => project.asJson
    }
  }
}
