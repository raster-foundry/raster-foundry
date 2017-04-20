package com.azavea.rf.tool.op

import io.circe._
import io.circe.syntax._
import io.circe.generic.auto._
import cats.data._

import java.net.URI
import java.util.UUID


/** The type [[Interpreter.Interpreted]] is either a successfully interpreted AST
  *  or else a list of all the failures the Interpreter runs into. Those errors are
  *  instances of InterpreterError.
  */
sealed trait InterpreterError {
  val id: UUID
  def repr: String
}

/** An unbound parameter encountered during evaluation  */
case class MissingParameter(id: UUID) extends InterpreterError {
  def repr = s"Unbound parameter encountered, unable to evaluate"
}

/** An error encountered when a bound parameter's source can't be resolved */
case class RasterRetrievalError(id: UUID, refId: UUID) extends InterpreterError {
  def repr = s"Unable to retrieve raster for $refId"
}

object InterpreterError {
  implicit val encodeInterpreterErrors: Encoder[InterpreterError] =
    new Encoder[InterpreterError] {
      final def apply(err: InterpreterError): Json = JsonObject.fromMap {
        Map("node" -> err.id.asJson, "reason" -> err.repr.asJson)
      }.asJson
    }
}

