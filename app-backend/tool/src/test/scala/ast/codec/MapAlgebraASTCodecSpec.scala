package com.azavea.rf.tool.ast.codec

import com.azavea.rf.tool.ast._
import com.azavea.rf.tool.eval._
import com.azavea.rf.tool.Generators._

import org.scalacheck._
import org.scalacheck.Gen._
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Prop.forAll
import org.scalatest.prop._
import org.scalatest._
import io.circe._
import io.circe.parser._
import io.circe.syntax._
import cats.syntax.either._

import java.util.UUID


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
