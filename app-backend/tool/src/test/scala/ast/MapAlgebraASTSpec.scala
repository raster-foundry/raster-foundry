package com.azavea.rf.tool.ast

import geotrellis.raster.op._
import org.scalatest._
import io.circe._
import io.circe.parser._
import io.circe.syntax._
import cats.syntax.either._

import java.util.UUID


class MapAlgebraASTSpec extends FunSpec with Matchers {

  def testSource() = MapAlgebraAST.RFMLRasterSource.empty

  it("Can find subtrees by ID") {
    val src1 = MapAlgebraAST.RFMLRasterSource.empty
    val src2 = MapAlgebraAST.RFMLRasterSource.empty
    val src3 = MapAlgebraAST.RFMLRasterSource.empty
    val src4 = MapAlgebraAST.RFMLRasterSource.empty
    val uberAst = src1 + src2 * src3 / src4

    uberAst.find(src4.id) should be (Some(src4))
  }
}
