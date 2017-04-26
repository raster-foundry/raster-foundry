package com.azavea.rf

import cats.data.NonEmptyList
import com.azavea.rf.tool.eval.DatabaseError
import java.util.UUID

import scala.concurrent.{ExecutionContext, Future}

import cats._
import cats.data.OptionT
import cats.data.Validated.{Invalid, Valid}
import cats.implicits._
import com.azavea.rf.database.Database
import com.azavea.rf.database.tables.{ToolRuns, Tools}
import com.azavea.rf.datamodel.User
import com.azavea.rf.tool.ast.MapAlgebraAST
import com.azavea.rf.tool.eval.Interpreter
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
      params  <- OptionT.fromOption[Future](maybeThrow(toolRun.executionParameters.as[EvalParams]))
      ast     <- OptionT.fromOption[Future](maybeThrow(tool.definition.as[MapAlgebraAST]))
    } yield {
      Interpreter.interpretPure[M](ast, params)
    }

    result.value.map({
      case Some(Valid(a)) => a
      case Some(Invalid(nel)) => throw InterpreterException(nel)
      case None => throw InterpreterException(NonEmptyList.of(DatabaseError(toolRunId)))
    })
  }

}
