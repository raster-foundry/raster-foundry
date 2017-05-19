package com.azavea.rf.tool.params

import com.azavea.rf.tool.ast._
import com.azavea.rf.tool.eval._
import com.azavea.rf.tool.Generators._

import org.scalacheck.Prop.forAll
import org.scalatest.prop._
import org.scalatest._
import io.circe._
import io.circe.parser._
import io.circe.syntax._
import cats.syntax.either._

import scala.util.Random
import java.util.UUID


class EvalParamsSpec extends PropSpec with Matchers with Checkers {
  property("bijective serialization") {
    check(forAll(genEvalParams) { (params: EvalParams) =>
      val json = params.asJson
      val params2 = json.as[EvalParams].toOption
      val json2 = params2.asJson
      params == params2
      json == json2
    })
  }
}
