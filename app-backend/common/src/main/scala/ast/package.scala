package com.azavea.rf.common

import com.azavea.rf.database.Database
import com.azavea.rf.database.tables.{ToolRuns, Tools}
import com.azavea.rf.datamodel.{Tool, ToolRun, User}
import com.azavea.rf.tool.ast.MapAlgebraAST
import com.azavea.rf.tool.ast.MapAlgebraAST._
import com.azavea.rf.tool.ast.assembleSubstitutions
import com.azavea.rf.tool.eval.{ASTDecodeError, DatabaseError, PureInterpreter}
import com.azavea.rf.tool.params._

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
    PureInterpreter.interpretPure[M](ast, true) match {
      case Valid(a) => a
      case Invalid(nel) => throw InterpreterException(nel)
    }

  /** Validate an AST, given some ToolRun. In the case of success, returns
    * the zero element of some specified Monoid.
    */
  def validateTreeWithSources[M: Monoid](ast: MapAlgebraAST): M =
    PureInterpreter.interpretPure[M](ast, false) match {
      case Valid(a) => a
      case Invalid(nel) => throw InterpreterException(nel)
    }
}
