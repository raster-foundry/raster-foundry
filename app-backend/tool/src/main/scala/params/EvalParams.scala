package com.azavea.rf.tool.params

import java.util.UUID

import cats.syntax.either._
import com.azavea.rf.bridge._
import com.azavea.rf.tool.ast._
import geotrellis.vector.MultiPolygon
import io.circe._
import io.circe.syntax._
import io.circe.generic.JsonCodec

// --- //

/* Yes, it is correct that all three of these fields can be empty. It is
 * possible to construct a legal AST that will produce results yet has no
 * external data sources.
 */
case class EvalParams(
  sources: Map[UUID, RFMLRaster] = Map(),
  metadata: Map[UUID, NodeMetadata] = Map(),
  overrides: Map[UUID, ParamOverride] = Map()
)

object EvalParams {

  implicit val encodeEvalParams: Encoder[EvalParams] =
    Encoder.forProduct3("sources", "metadata", "overrides")(ep => (ep.sources, ep.metadata, ep.overrides))

  implicit val decodeEvalParams: Decoder[EvalParams] = new Decoder[EvalParams] {
    final def apply(c: HCursor): Decoder.Result[EvalParams] = {
      val sources = c.get[Map[UUID, RFMLRaster]]("sources").getOrElse(Map.empty)
      val metas = c.get[Map[UUID, NodeMetadata]]("metadata").getOrElse(Map.empty)
      val overrides = c.get[Map[UUID, ParamOverride]]("overrides").getOrElse(Map.empty)

      Right(EvalParams(sources, metas, overrides))
    }
  }
}

/** A sum type which allows us hold override values of varying types in the
  * same `Map` in a type safe way.
  */
sealed trait ParamOverride

object ParamOverride {

  @JsonCodec case class Classification(classMap: ClassMap) extends ParamOverride
  @JsonCodec case class Constant(constant: Double) extends ParamOverride
  @JsonCodec case class Masking(mask: MultiPolygon) extends ParamOverride

  /** Add more possibilities as necessary with the `.orElse` mechanic
    * (the "Alternative" pattern).
    */
  implicit val dec: Decoder[ParamOverride] = Decoder.instance[ParamOverride]({ po =>
    po.as[Classification]
      .orElse(po.as[Constant])
      .orElse(po.as[Masking])
  })

  implicit val enc: Encoder[ParamOverride] = new Encoder[ParamOverride] {
    def apply(overrides: ParamOverride): Json = overrides match {
      case c: Classification => c.asJson
      case c: Constant => c.asJson
      case m: Masking => m.asJson
    }
  }
}
