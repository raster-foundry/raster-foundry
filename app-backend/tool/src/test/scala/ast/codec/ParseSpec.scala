package com.azavea.rf.tool.ast.codec

import com.azavea.rf.tool.ast._

import geotrellis.raster.op._

import org.scalatest._
import io.circe._
import io.circe.parser._
import io.circe.syntax._
import cats.syntax.either._

import java.util.UUID


class ParseSpec extends FunSpec with Matchers {
  import MapAlgebraCodec._

  it("parses apply of +") {
    println(MapAlgebraAST.Addition(List(), UUID.randomUUID(), None).asJson)
  }
}
