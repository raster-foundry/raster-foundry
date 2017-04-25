package com.azavea.rf.tool.ast.codec

import com.azavea.rf.tool.ast._
import com.azavea.rf.tool.eval._

import org.scalatest._
import io.circe._
import io.circe.parser._
import io.circe.syntax._
import cats.syntax.either._

import java.util.UUID


class MapAlgebraASTCodecSpec extends FunSpec with Matchers {

  def randomSource: MapAlgebraAST.Source =
    MapAlgebraAST.Source(UUID.randomUUID, None)

  def ndvi(red: MapAlgebraAST, nir: MapAlgebraAST): MapAlgebraAST =
    (red - nir) / (red + nir)

  def ndviDiff: MapAlgebraAST =
    ndvi(randomSource, randomSource).classify(ClassBreaks(Map())) - ndvi(randomSource, randomSource).classify(ClassBreaks(Map()))


  it("Can round trip an NDVI difference definition") {
    val classBreaks = ClassBreaks(Map())

    val jsonOut1 = ndviDiff.asJson
    val jsonIn = jsonOut1.as[MapAlgebraAST].toOption
    val jsonOut2 = jsonIn.map(_.asJson)

    jsonOut1 should be (jsonOut2.get)
  }

  it("Can round trip an arbitrary symbol trees") {
    val tree = (randomSource - randomSource + randomSource * randomSource / randomSource).classify(ClassBreaks(Map()))

    val jsonOut1 = tree.asJson
    val jsonIn = jsonOut1.as[MapAlgebraAST].toOption
    val jsonOut2 = jsonIn.map(_.asJson)

    jsonOut1 should be (jsonOut2.get)
  }
}
