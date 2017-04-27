package com.azavea.rf.tool.eval

import java.util.UUID

import io.circe._
import io.circe.syntax._


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
  def repr = s"Unbound parameter at $id encountered, unable to evaluate"
}

/** An error encountered when a bound parameter's source can't be resolved */
case class RasterRetrievalError(id: UUID, refId: UUID) extends InterpreterError {
  def repr = s"Unable to retrieve raster for $refId"
}

case class DatabaseError(id: UUID) extends InterpreterError {
  def repr = s"Unable to retrieve ToolRun $id or its associated Tool from the database"
}

case class ASTDecodeError(id: UUID, msg: DecodingFailure) extends InterpreterError {
  def repr = s"Unable to decode the AST associated with ToolRun ${id}: ${msg}"
}

object InterpreterError {
  implicit val encodeInterpreterErrors: Encoder[InterpreterError] =
    new Encoder[InterpreterError] {
      final def apply(err: InterpreterError): Json = JsonObject.fromMap {
        Map("node" -> err.id.asJson, "reason" -> err.repr.asJson)
      }.asJson
    }
}
