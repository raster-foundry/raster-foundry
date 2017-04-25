package com.azavea.rf.tool.ast

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
    val uberAst = src1 + src2 * src3 / src4

    uberAst.find(src4.id) should be (Some(src4))
  }
}
