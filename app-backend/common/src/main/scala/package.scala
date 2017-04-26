package com.azavea.rf

import java.util.UUID

import scala.concurrent.{ExecutionContext, Future}

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
  def maybeThrow[A <: Throwable, B, C](e: Either[A, B])(f: B => C): Option[C] = e match {
    case Right(a) => Some(f(a))
    case Left(failure) => throw failure
  }

  def validateAST(
    toolRunId: UUID,
    user: User
  )(implicit database: Database, ec: ExecutionContext): OptionT[Future, Boolean] = {

    val result: OptionT[Future, Interpreter.Interpreted[Unit]] = for {
      toolRun <- OptionT(database.db.run(ToolRuns.getToolRun(toolRunId, user)))
      tool    <- OptionT(Tools.getTool(toolRun.tool, user))
      params  <- OptionT.fromOption[Future](maybeThrow(toolRun.executionParameters.as[EvalParams])(identity))
      ast     <- OptionT.fromOption[Future](maybeThrow(tool.definition.as[MapAlgebraAST])(identity))
    } yield {
      Interpreter.interpretPure(ast, params)
    }

    result.map({
      case Valid(_) => true // TODO: What to return on success?
      case Invalid(nel) => throw InterpreterException(nel)
    })
  }

}
