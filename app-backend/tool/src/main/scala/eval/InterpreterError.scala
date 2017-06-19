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

case class IncorrectArgCount(id: UUID, expected: Int, actual: Int) extends InterpreterError {
  def repr = s"Operation ${id} was given ${actual} args, but expected ${expected}"
}

case class UnhandledCase(id: UUID) extends InterpreterError {
  def repr = s"Some branch of Interpreter logic has yet to be implemented: ${id}"
}

case class NoBandGiven(id: UUID) extends InterpreterError {
  def repr = s"No band value given for Scene ${id}"
}

case class AttributeStoreFetchError(id: UUID) extends InterpreterError {
  def repr = s"Unable to fetch an S3AttributeStore for Scene ${id}"
}

/** An error encountered when a bound parameter's source can't be resolved */
case class RasterRetrievalError(id: UUID) extends InterpreterError {
  def repr = s"Unable to retrieve raster for $id"
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
