package com.azavea.rf.tool.ast.codec

import cats.syntax.either._
import com.azavea.rf.tool.Generators._
import com.azavea.rf.tool.ast._
import io.circe.syntax._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatest.prop._


class MapAlgebraASTCodecSpec extends PropSpec with Checkers {
  property("bijective serialization") {
    check(forAll(genMapAlgebraAST()) { (ast: MapAlgebraAST) =>
      val json = ast.asJson
      val ast2 = json.as[MapAlgebraAST].toOption
      val json2 = ast2.asJson
      ast == ast2
      json == json2
    })
  }
}
