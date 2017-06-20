package com.azavea.rf.tool.params

import java.util.UUID

import cats.syntax.either._
import com.azavea.rf.tool.ast._
import io.circe._
import io.circe.syntax._
import io.circe.generic.JsonCodec

case class EvalParams(
  sources: Map[UUID, RFMLRaster] = Map(),
  metadata: Map[UUID, NodeMetadata] = Map(),
  overrides: Map[UUID, ParamOverride] = Map()
)

object EvalParams {

  implicit val encodeEvalParams: Encoder[EvalParams] =
    Encoder.forProduct2("sources", "metadata")(ep => (ep.sources, ep.metadata))

  implicit val decodeEvalParams: Decoder[EvalParams] = new Decoder[EvalParams] {
    final def apply(c: HCursor): Decoder.Result[EvalParams] = {
      val sources = c.get[Map[UUID, RFMLRaster]]("sources").getOrElse(Map.empty)
      val metas = c.get[Map[UUID, NodeMetadata]]("metadata").getOrElse(Map.empty)
      val overrides = c.get[Map[UUID, ParamOverride]]("overrides").getOrElse(Map.empty)

      Right(EvalParams(sources, metas, overrides))
    }
  }
}

sealed trait ParamOverride

object ParamOverride {

  @JsonCodec
  case class Classification(classMap: ClassMap) extends ParamOverride

  /** Add more possibilities as necessary with the `.orElse` mechanic
    * (the "Alternative" pattern).
    */
  implicit val dec: Decoder[ParamOverride] = Decoder.instance[ParamOverride]({ po =>
    po.downField("classify").as[Classification]
  })

  implicit val enc: Encoder[ParamOverride] = new Encoder[ParamOverride] {
    def apply(overrides: ParamOverride): Json = overrides match {
      case c: Classification => c.asJson
    }
  }
}
