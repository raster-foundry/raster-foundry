package com.azavea.rf.tool.params

import java.util.UUID

import cats.syntax.either._
import com.azavea.rf.tool.ast._
import io.circe._

case class EvalParams(sources: Map[UUID, RFMLRaster] = Map(), metadata: Map[UUID, NodeMetadata] = Map())

object EvalParams {
  implicit val encodeEvalParams: Encoder[EvalParams] =
    Encoder.forProduct2("sources", "metadata")(ep => (ep.sources, ep.metadata))

  implicit val decodeEvalParams: Decoder[EvalParams] = new Decoder[EvalParams] {
    final def apply(c: HCursor): Decoder.Result[EvalParams] = {
      val sources = c.get[Map[UUID, RFMLRaster]]("sources").getOrElse(Map())
      val overrides = c.get[Map[UUID, NodeMetadata]]("metadata").getOrElse(Map())

      Right(EvalParams(sources, overrides))
    }
  }
}
