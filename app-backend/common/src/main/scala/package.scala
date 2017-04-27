package com.azavea.rf

import java.util.UUID

import scala.concurrent.{ExecutionContext, Future}

import cats._
import cats.data.{NonEmptyList, OptionT}
import cats.data.Validated.{Invalid, Valid}
import cats.implicits._
import com.azavea.rf.database.Database
import com.azavea.rf.database.tables.{ToolRuns, Tools}
import com.azavea.rf.datamodel.{Tool, ToolRun, User}
import com.azavea.rf.tool.ast.MapAlgebraAST
import com.azavea.rf.tool.eval.{ASTDecodeError, DatabaseError, Interpreter}
import com.azavea.rf.tool.params.EvalParams

package object common {

  /** Convert an [[Either]] to an [[Option]], or throw the error. */
  def maybeThrow[A <: Throwable, B](e: Either[A, B]): Option[B] = e match {
    case Right(a) => Some(a)
    case Left(failure) => throw failure
  }

  /** Validate an AST, given some ToolRun. In the case of success, returns
    * the zero element of some specified Monoid.
    */
  def validateAST[M: Monoid](
    toolRunId: UUID,
    user: User
  )(implicit database: Database, ec: ExecutionContext): Future[M] = {

    val result: OptionT[Future, Interpreter.Interpreted[M]] = for {
      toolRun <- OptionT(database.db.run(ToolRuns.getToolRun(toolRunId, user)))
      tool    <- OptionT(Tools.getTool(toolRun.tool, user))
    } yield {
      validateASTPure[M](toolRun, tool)
    }

    result.value.map({
      case Some(Valid(a)) => a
      case Some(Invalid(nel)) => throw InterpreterException(nel)
      case None => throw InterpreterException(NonEmptyList.of(DatabaseError(toolRunId)))
    })
  }

  def validateASTPure[M: Monoid](tr: ToolRun, t: Tool.WithRelated): Interpreter.Interpreted[M] = {

    (t.definition.as[MapAlgebraAST] |@| tr.executionParameters.as[EvalParams]).map(
      Interpreter.interpretPure[M](_, _)
    ) match {
      case Right(a) => a
      case Left(err) => Invalid(NonEmptyList.of(ASTDecodeError(tr.id, err)))
    }
  }
}
