package com.rasterfoundry.common.datamodel

import java.util.UUID

import cats.implicits._
import com.rasterfoundry.common._
import com.rasterfoundry.common.ast.MapAlgebraAST
import geotrellis.vector.MultiPolygon
import io.circe._
import io.circe.generic.semiauto._
import io.circe.parser._
import io.circe.syntax._

final case class InputDefinition(resolution: Int, style: InputStyle)

object InputDefinition {
  implicit val dec: Decoder[InputDefinition] = Decoder.instance(
    c =>
      (
        c.downField("resolution").as[Int],
        c.downField("style")
          .as[InputStyle]
      ).mapN(InputDefinition.apply)
  )

  implicit val enc: Encoder[InputDefinition] =
    Encoder.forProduct2("resolution", "style")(u => (u.resolution, u.style))
}

sealed trait InputStyle

/** There are two varieties Export - those that involve an AST, which
  * performs Map Algebra and produces [[SinglebandGeoTiff]]s, and as-ingested
  * [[MultibandTile]] exports which can perform colour correction and do simple
  * cropping
  */
object InputStyle {
  implicit val decInputStyle: Decoder[InputStyle] = List[Decoder[InputStyle]](
    Decoder[SimpleInput].widen,
    Decoder[ASTInput].widen
  ).reduce { _ or _ }

  implicit val encInputStyle: Encoder[InputStyle] = Encoder.instance {
    case simple: SimpleInput =>
      simple.asJson
    case ast: ASTInput =>
      ast.asJson
  }

}

final case class SimpleInput(layers: Array[MosaicDefinition],
                             mask: Option[MultiPolygon])
    extends InputStyle

object SimpleInput {
  implicit val encSimpleInput: Encoder[SimpleInput] = deriveEncoder[SimpleInput]
  implicit val decSimpleInput: Decoder[SimpleInput] = deriveDecoder[SimpleInput]

  def asInputStyle(simple: SimpleInput): InputStyle = simple
}

final case class ASTInput(ast: MapAlgebraAST,
                          /* Ingest locations of "singleton" scenes that appear in the EvalParams */
                          ingestLocs: Map[UUID, String],
                          /* Ingest locations (and implicit ordering) of each scene in each project */
                          projectScenes: Map[UUID, List[(UUID, String)]])
    extends InputStyle

object ASTInput {
  import com.rasterfoundry.common.ast.codec.MapAlgebraCodec._
  Encoder[MapAlgebraAST]
  Encoder[Map[UUID, String]]
  Encoder[Map[UUID, List[(UUID, String)]]]
  implicit val encASTInput: Encoder[ASTInput] = deriveEncoder[ASTInput]
  implicit val decASTInput: Decoder[ASTInput] = deriveDecoder[ASTInput]

  def asInputStyle(ast: ASTInput): InputStyle = ast
}
