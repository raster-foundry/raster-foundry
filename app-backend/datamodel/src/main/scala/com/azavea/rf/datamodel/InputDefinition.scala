package com.azavea.rf.datamodel

import java.util.UUID

import cats.implicits._
import com.azavea.rf.bridge._
import com.azavea.rf.tool.ast.MapAlgebraAST
import com.azavea.rf.tool.params.EvalParams
import geotrellis.vector.MultiPolygon
import io.circe._
import io.circe.generic.JsonCodec
import io.circe.syntax._

/** 2017 May 19 @ 13:30
  * There are two varieties Export - those that involve an AST, which
  * performs Map Algebra and produces [[SinglebandGeoTiff]]s, and as-ingested
  * [[MultibandTile]] exports which can perform colour correction and do simple
  * cropping / stitching.
  */
case class InputDefinition(
  projectId: Option[UUID],  // TODO: Might not be necessary.
  resolution: Int,
  style: Either[SimpleInput, ASTInput]
)

object InputDefinition {
  implicit val dec: Decoder[InputDefinition] = Decoder.instance(c =>
    (c.downField("projectId").as[Option[UUID]]
      |@| c.downField("resolution").as[Int]
      |@| c.downField("style").as[SimpleInput].map(Left(_))
           .orElse(c.downField("style").as[ASTInput].map(Right(_)))
    ).map(InputDefinition.apply)
  )

  implicit val eitherEnc: Encoder[Either[SimpleInput, ASTInput]] = new Encoder[Either[SimpleInput, ASTInput]] {
    final def apply(a: Either[SimpleInput, ASTInput]): Json = a match {
      case Left(l) => l.asJson
      case Right(r) => r.asJson
    }
  }

  implicit val enc: Encoder[InputDefinition] =
    Encoder.forProduct3("projectId", "resolution", "style")(u =>
      (u.projectId, u.resolution, u.style)
    )
}

@JsonCodec
case class SimpleInput(layers: Array[ExportLayerDefinition], mask: Option[MultiPolygon])

@JsonCodec
case class ASTInput(
  ast: MapAlgebraAST,
  params: EvalParams,
  /* Ingest locations of "singleton" scenes that appear in the EvalParams */
  ingestLocs: Map[UUID, String],
  /* Ingest locations (and implicit ordering) of each scene in each project */
  projectScenes: Map[UUID, List[(UUID, String)]]
)
