package com.rasterfoundry.common

import com.rasterfoundry.datamodel.{Tool, ToolRun, User}
import com.rasterfoundry.tool.ast.MapAlgebraAST
import com.rasterfoundry.tool.eval._

import com.azavea.maml.error._
import cats._
import cats.data._
import cats.implicits._
import cats.data.Validated.{Invalid, Valid}
import io.circe._

import java.lang.IllegalArgumentException
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

package object ast {

  /** Validate an AST, given some ToolRun. In the case of success, returns
    * the zero element of some specified Monoid.
    */
  def validateTree[M: Monoid](ast: MapAlgebraAST): M =
    PureInterpreter.interpret[M](ast, true) match {
      case Valid(a) => a
      case Invalid(nel) =>
        throw new Exception("Could not interpret AST")
    }

  /** Validate an AST, given some ToolRun. In the case of success, returns
    * the zero element of some specified Monoid.
    */
  def validateTreeWithSources[M: Monoid](ast: MapAlgebraAST): M =
    PureInterpreter.interpret[M](ast, false) match {
      case Valid(a) => a
      case Invalid(nel) =>
        throw new Exception("Could not interpret AST")
    }
}
