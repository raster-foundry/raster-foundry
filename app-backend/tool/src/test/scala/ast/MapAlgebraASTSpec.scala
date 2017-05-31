package com.azavea.rf.tool.ast

import com.azavea.rf.tool.ast.MapAlgebraAST._
import com.azavea.rf.tool.ast.codec._
import com.azavea.rf.tool.eval._

import org.scalatest._
import io.circe._
import io.circe.parser._
import io.circe.syntax._
import cats.syntax.either._

import java.util.UUID


class MapAlgebraASTSpec extends FunSpec with Matchers {

  def randomSourceAST = MapAlgebraAST.Source(UUID.randomUUID, None)

  it("Can find subtrees by ID") {
    val src1 = randomSourceAST
    val src2 = randomSourceAST
    val src3 = randomSourceAST
    val src4 = randomSourceAST
    val src5 = randomSourceAST
    val src6 = randomSourceAST
    val uberAst = src1 + src2 * src3 / src4 max src5 min src6

    uberAst.find(src4.id) should be (Some(src4))
  }

  it("Can substitute ToolRef branches") {
    val src1 = randomSourceAST
    val replacement = randomSourceAST + randomSourceAST
    val ref = ToolReference(UUID.randomUUID, replacement.id)
    val preSub = src1 + ref
    val postSub = src1 + replacement

    preSub.substitute(Map(ref.toolId -> replacement)).get.args.toSeq should be (postSub.args.toSeq)
  }
}
