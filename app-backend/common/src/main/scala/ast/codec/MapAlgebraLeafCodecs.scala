package com.rasterfoundry.common.ast.codec

import com.rasterfoundry.common.ast._

import geotrellis.raster.{io => _, _}
import io.circe._
import io.circe.syntax._

import java.security.InvalidParameterException

trait MapAlgebraLeafCodecs {
  implicit def mapAlgebraDecoder: Decoder[MapAlgebraAST]
  implicit def mapAlgebraEncoder: Encoder[MapAlgebraAST]

  /** TODO: Add codec paths besides `raster source` and `operation` when supported */
  implicit def mapAlgebraLeafDecoder =
    Decoder.instance[MapAlgebraAST.MapAlgebraLeaf] { ma =>
      ma.typeOpt match {
        case Some("ref") =>
          ma.as[MapAlgebraAST.ToolReference]
        case Some("const") =>
          ma.as[MapAlgebraAST.Constant]
        case Some("layerSrc") =>
          ma.as[MapAlgebraAST.LayerRaster]
        case Some("projectSrc") =>
          ma.as[MapAlgebraAST.ProjectRaster]
        case _ =>
          Left(DecodingFailure(s"Unrecognized leaf node: $ma", ma.history))
      }
    }

  implicit def mapAlgebraLeafEncoder: Encoder[MapAlgebraAST.MapAlgebraLeaf] =
    new Encoder[MapAlgebraAST.MapAlgebraLeaf] {
      def apply(ast: MapAlgebraAST.MapAlgebraLeaf): Json = ast match {
        case reference: MapAlgebraAST.ToolReference =>
          reference.asJson
        case const: MapAlgebraAST.Constant =>
          const.asJson
        case layerSrc: MapAlgebraAST.LayerRaster =>
          layerSrc.asJson
        case projectSrc: MapAlgebraAST.ProjectRaster =>
          projectSrc.asJson
        case _ =>
          throw new InvalidParameterException(s"Unrecognized AST: $ast")
      }
    }

  implicit lazy val decodeLayerSource: Decoder[MapAlgebraAST.LayerRaster] =
    Decoder.forProduct5("id", "layerId", "band", "celltype", "metadata")(
      MapAlgebraAST.LayerRaster.apply)
  implicit lazy val encodeLayerSource: Encoder[MapAlgebraAST.LayerRaster] =
    Encoder.forProduct6("type",
                        "id",
                        "layerId",
                        "band",
                        "celltype",
                        "metadata")(src =>
      (src.`type`, src.id, src.layerId, src.band, src.celltype, src.metadata))

  implicit lazy val decodeProjectSource: Decoder[MapAlgebraAST.ProjectRaster] =
    Decoder.forProduct5("id", "projId", "band", "celltype", "metadata")(
      MapAlgebraAST.ProjectRaster.apply)
  implicit lazy val encodeProjectSource: Encoder[MapAlgebraAST.ProjectRaster] =
    Encoder.forProduct6("type", "id", "projId", "band", "celltype", "metadata")(
      src =>
        (src.`type`, src.id, src.projId, src.band, src.celltype, src.metadata))

  implicit lazy val decodeConstant: Decoder[MapAlgebraAST.Constant] =
    Decoder.forProduct3("id", "constant", "metadata")(
      MapAlgebraAST.Constant.apply)
  implicit lazy val encodeConstant: Encoder[MapAlgebraAST.Constant] =
    Encoder.forProduct4("type", "id", "constant", "metadata")(const =>
      (const.`type`, const.id, const.constant, const.metadata))

  implicit lazy val decodeReference: Decoder[MapAlgebraAST.ToolReference] =
    Decoder.forProduct2("id", "toolId")(MapAlgebraAST.ToolReference.apply)
  implicit lazy val encodeReference: Encoder[MapAlgebraAST.ToolReference] =
    Encoder.forProduct3("type", "id", "toolId")(ref =>
      (ref.`type`, ref.id, ref.toolId))

  implicit lazy val celltypeDecoder: Decoder[CellType] =
    Decoder[String].map({ CellType.fromName(_) })
  implicit lazy val celltypeEncoder: Encoder[CellType] =
    Encoder.encodeString.contramap[CellType]({ CellType.toName(_) })
}
