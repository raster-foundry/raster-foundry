package com.azavea.rf.tool.ast

import java.util.UUID

import org.scalatest._

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
}
