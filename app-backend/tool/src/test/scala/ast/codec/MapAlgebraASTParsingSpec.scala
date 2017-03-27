package com.azavea.rf.tool.ast.codec

import com.azavea.rf.tool.ast._

import geotrellis.raster.op._
import org.scalatest._
import io.circe._
import io.circe.parser._
import io.circe.syntax._
import cats.syntax.either._

import java.util.UUID


class MapAlgebraASTParsingSpec extends FunSpec with Matchers {

  def ndvi(red: MapAlgebraAST, nir: MapAlgebraAST): MapAlgebraAST =
    (red - nir) / (red + nir)

  def ndviDiff(red1: MapAlgebraAST, nir1: MapAlgebraAST, red2: MapAlgebraAST, nir2: MapAlgebraAST, breaks: ClassBreaks): MapAlgebraAST =
    ndvi(red1, nir1).reclassify(breaks) - ndvi(red2, nir2).reclassify(breaks)


  it("Can round trip an NDVI difference definition") {
    val red = MapAlgebraAST.RFMLRasterSource.empty
    val nir = MapAlgebraAST.RFMLRasterSource.empty
    val classBreaks = ClassBreaks(Map())

    val jsonOut1 = ndviDiff(red, nir, red, nir, classBreaks).asJson
    val jsonIn = jsonOut1.as[MapAlgebraAST].toOption
    val jsonOut2 = jsonIn.map(_.asJson)

    jsonOut1 should be (jsonOut2.get)
  }

  it("Can round trip an arbitrary symbol trees") {
    val src = MapAlgebraAST.RFMLRasterSource.empty
    val classBreaks = ClassBreaks(Map())
    val tree = (src - src + src * src / src).reclassify(classBreaks)

    val jsonOut1 = tree.asJson
    val jsonIn = jsonOut1.as[MapAlgebraAST].toOption
    val jsonOut2 = jsonIn.map(_.asJson)

    jsonOut1 should be (jsonOut2.get)
  }

  it("Identify unbound parameters") {
    val red = MapAlgebraAST.RFMLRasterSource.empty
    val nir = MapAlgebraAST.RFMLRasterSource.empty
    val classBreaks = ClassBreaks(Map())

    val operation = ndviDiff(red, nir, red, nir, classBreaks)

    operation.evaluable should be (false)
  }

  it("Can list unbound parameters") {
    val red1 = MapAlgebraAST.RFMLRasterSource.empty
    val nir1 = MapAlgebraAST.RFMLRasterSource.empty

    val red2 = MapAlgebraAST.RFMLRasterSource.empty
    val nir2 = MapAlgebraAST.RFMLRasterSource.empty
    val classBreaks = ClassBreaks(Map())

    val operation = ndviDiff(red1, nir1, red2, nir2, classBreaks)

    operation.unbound should be (Seq(red1, nir1, red2, nir2))
  }
}
