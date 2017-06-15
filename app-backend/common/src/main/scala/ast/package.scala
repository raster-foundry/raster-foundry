package com.azavea.rf.common

import com.azavea.rf.database.Database
import com.azavea.rf.database.tables.{ToolRuns, Tools}
import com.azavea.rf.datamodel.{Tool, ToolRun, User}
import com.azavea.rf.tool.ast.MapAlgebraAST
import com.azavea.rf.tool.ast.MapAlgebraAST._
import com.azavea.rf.tool.eval.{ASTDecodeError, DatabaseError, Interpreter}
import com.azavea.rf.tool.params.EvalParams

import cats._
import cats.data._
import cats.implicits._
import cats.data.Validated.{Invalid, Valid}
import io.circe._

import java.lang.IllegalArgumentException
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

package object ast {

  /** Collect resources necessary to carry out substitutions on  ASTs while avoiding cycles */
  def assembleSubstitutions(
    ast: MapAlgebraAST,
    user: User,
    assembled: Map[UUID, MapAlgebraAST] = Map()
  )(implicit database: Database, ec: ExecutionContext): OptionT[Future, Map[UUID, MapAlgebraAST]] =
    ast match {
      case ToolReference(id, refId) =>
        OptionT(Tools.getTool(refId, user)).flatMap({ referent =>
          val astPatch = referent.definition.as[MapAlgebraAST].valueOr(throw _)
          // Here's where we can hope to catch cycles as we traverse down
          //  the tree (keeping track of previously encountered references)
          if (assembled.keys.toList.contains(refId))
            throw new IllegalArgumentException(s"Repeated substitution $refId found; likely cyclical reference")
          else
            assembleSubstitutions(astPatch, user, assembled ++ Map(refId -> astPatch))
        })
      case op: MapAlgebraAST.Operation =>
        val childSubstitutions = op.args.map({ arg => assembleSubstitutions(arg, user, assembled) }).sequence
        childSubstitutions.map({ substitutions =>
          substitutions.foldLeft(Map[UUID, MapAlgebraAST]())({ case (map, subs) => map ++ subs }) ++ assembled
        })
      case _ =>
        OptionT.pure[Future, Map[UUID, MapAlgebraAST]](assembled)
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
      oldAst  <- OptionT.pure[Future, MapAlgebraAST](tool.definition.as[MapAlgebraAST].valueOr(throw _))
      subs    <- assembleSubstitutions(oldAst, user)
      ast     <- OptionT.fromOption[Future](oldAst.substitute(subs))
      params  <- OptionT.pure[Future, EvalParams](toolRun.executionParameters.as[EvalParams].valueOr(throw _))
    } yield {
      Interpreter.interpretPure[M](ast, params.sources)
    }

    result.value.map({
      case Some(Valid(a)) => a
      case Some(Invalid(nel)) => throw InterpreterException(nel)
      case None => throw InterpreterException(NonEmptyList.of(DatabaseError(toolRunId)))
    })
  }
}
