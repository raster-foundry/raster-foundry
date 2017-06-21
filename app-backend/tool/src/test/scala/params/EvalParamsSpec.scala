package com.azavea.rf.tool.params

import cats.syntax.either._
import com.azavea.rf.tool.Generators._
import io.circe.syntax._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatest.prop._


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
