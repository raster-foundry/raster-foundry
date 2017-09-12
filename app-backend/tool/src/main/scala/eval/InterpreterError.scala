package com.azavea.rf.tool.eval

import com.azavea.rf.tool.ast._

import io.circe._
import io.circe.syntax._

import java.util.UUID


/** The type [[Interpreter.Interpreted]] is either a successfully interpreted AST
  *  or else a list of all the failures the Interpreter runs into. Those errors are
  *  instances of InterpreterError.
  */
sealed trait InterpreterError {
  val scope: String
  def repr: String
}

/** An unbound parameter encountered during evaluation  */
case class MissingParameter(id: UUID) extends InterpreterError {
  val scope = id.toString
  def repr = s"Unbound parameter at $scope encountered, unable to evaluate"
}

case class IncorrectArgCount(id: UUID, expected: Int, actual: Int) extends InterpreterError {
  val scope = id.toString
  def repr = s"Operation ${scope} was given ${actual} args, but expected ${expected}"
}

case class UnhandledCase(id: UUID) extends InterpreterError {
  val scope = id.toString
  def repr = s"Some branch of Interpreter logic has yet to be implemented: ${id}"
}

case class UnsubstitutedRef(id: UUID) extends InterpreterError {
  val scope = id.toString
  def repr = s"Unsubstituted Tool reference found: ${id}"
}

case class NoSourceLeaves(id: UUID) extends InterpreterError {
  val scope = id.toString
  def repr = s"The Operation ${id} has only Constant leaves"
}

case class NoBandGiven(id: UUID) extends InterpreterError {
  val scope = "i/o"
  def repr = s"No band value given for Scene ${id}"
}

case class AttributeStoreFetchError(id: UUID) extends InterpreterError {
  val scope = "i/o"
  def repr = s"Unable to fetch an S3AttributeStore for Scene ${id}"
}

/** An error encountered when a bound parameter's source can't be resolved */
case class RasterRetrievalError(rfmlraster: RFMLRaster) extends InterpreterError {
  val scope = "i/o"
  def repr = s"Unable to retrieve raster for ${rfmlraster.toString}"
}

case class DatabaseError(id: UUID) extends InterpreterError {
  val scope = "i/o"
  def repr = s"Unable to retrieve ToolRun $id or its associated Tool from the database"
}

case class ASTDecodeError(msg: DecodingFailure) extends InterpreterError {
  val scope = "global"
  def repr = s"Unable to decode AST: ${msg}"
}

case class InvalidOverride(id: UUID) extends InterpreterError {
  val scope = id.toString
  def repr = s"Node ${id} was given an incompatible override value"
}

case class LazyTileEvaluationError(ast: MapAlgebraAST) extends InterpreterError {
  val scope = ast.id.toString
  def repr = s"Unknown error during lazytile evaluation for $ast"
}

object InterpreterError {
  implicit val encodeInterpreterErrors: Encoder[InterpreterError] =
    new Encoder[InterpreterError] {
      final def apply(err: InterpreterError): Json = JsonObject.fromMap {
        Map("scope" -> err.scope.asJson, "reason" -> err.repr.asJson)
      }.asJson
    }
}
