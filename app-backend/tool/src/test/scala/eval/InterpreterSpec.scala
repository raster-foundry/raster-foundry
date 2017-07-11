package com.azavea.rf.tool.eval

import com.azavea.rf.tool.ast._
import com.azavea.rf.tool.eval._
import com.azavea.rf.tool.params._
import com.azavea.rf.tool.ast.MapAlgebraAST._

import io.circe._
import io.circe.syntax._
import cats.data.{NonEmptyList => NEL, _}
import cats.data.Validated._
import cats.implicits._
import org.scalatest._
import geotrellis.vector._
import geotrellis.raster._
import geotrellis.raster.render._
import geotrellis.raster.testkit._

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import java.util.UUID


class InterpreterSpec
    extends FunSpec
       with Matchers
       with MockInterpreterResources {

  it("should evaluate simple ast") {
    val src1 = randomSourceAST
    val src2 = randomSourceAST
    val tms = Interpreter.interpretTMS(
      ast = src1 - src2,
      sourceMapping = Map(src1.id -> tileSource(4), src2.id -> tileSource(5)),
      overrides = Map.empty,
      tileSource = goodSource
    )

    val ret = tms(0,1,1)
    val tile = Await.result(ret, 10.seconds) match {
      case Valid(lazytile) =>
        val tile = lazytile.evaluate
        requests.length should be (2)
        assertEqual(tile.get, createValueTile(256, -1).toArray)
      case i@Invalid(_) =>
        fail(s"Unable to parse tree for tile retrieval: $i")
    }
  }


  it("should not fetch unless eval paramaters are fully capable of filling out the AST") {
    requests = Nil
    val src1 = randomSourceAST
    val src2 = randomSourceAST
    val tms = Interpreter.interpretTMS(
      ast = src1 - src2,
      sourceMapping = Map(src1.id -> tileSource(4)),
      overrides = Map.empty,
      tileSource = goodSource
    )

    val ret = tms(0,1,1)
    val lt = Await.result(ret, 10.seconds)

    requests.length should be (1)
    lt should be (Invalid(NEL.of(MissingParameter(src2.id))))
  }

  /** A failed future is semantically different than successfully returned None. This test
    *  allows us to simulate a failure encountered while retrieving a tile as opposed to
    *  an empty region of some tile layout.
    */
  it("should deal with bad raster sourceMapping and aggregate multiple errors") {
    requests = Nil
    val src1 = randomSourceAST
    val src2 = randomSourceAST
    val badSource = (raster: RFMLRaster, buffer: Boolean, z: Int, x: Int, y: Int) => Future.failed { new Exception("Some exception") }

    val theTileSource = tileSource(4)
    val tms = Interpreter.interpretTMS(
      ast = src1 - src2,
      sourceMapping = Map(src1.id -> theTileSource),
      overrides = Map.empty,
      tileSource = badSource
    )

    val ret = tms(0,1,1)
    val lt = Await.result(ret, 10.seconds)

    requests should be (empty)
    lt should be (Invalid(NEL.of(MissingParameter(src2.id), RasterRetrievalError(src1.id, theTileSource.id))))
  }

  it("interpretPure - simple") {
    val src1 = randomSourceAST
    val src2 = randomSourceAST

    val tms = Interpreter.interpretPure[Unit](
      ast = src1 - src2,
      sourceMapping = Map(src1.id -> tileSource(4), src2.id -> tileSource(5))
    )

    tms shouldBe Valid(())

  }

  it("interpretPure - multiple errors") {
    val ast: MapAlgebraAST = Addition(List(Source.empty), UUID.randomUUID, None)

    Interpreter.interpretPure[Unit](ast, Map.empty) match {
      case Invalid(nel) => nel.size shouldBe 2
      case Valid(_) => fail
    }
  }

  it("should evaluate classification (case hit)") {
    requests = Nil
    // This breakmap should convert all cells (which are set to a value of 5) to 77
    val breakmap = ClassMap(Map(6.0 -> 77))
    val srcAST = randomSourceAST
    val tms = Interpreter.interpretTMS(
      ast = srcAST.classify(breakmap),
      sourceMapping = Map(srcAST.id -> tileSource(4)),
      overrides = Map.empty,
      tileSource = goodSource
    )
    println("Classification node: ", srcAST.classify(breakmap).asJson.noSpaces)

    val ret = tms(0, 1, 1)
    val op = Await.result(ret, 10.seconds) match {
      case Valid(lazyTile) =>
        val maybeTile = lazyTile.evaluate
        requests.length should be (1)
        assertEqual(maybeTile.get, createValueTile(256, 77))
      case i@Invalid(_) =>
        fail(s"$i")
    }
  }

  it("should evaluate classification (case miss)") {
    requests = Nil
    // This breakmap should convert all cells (which are set to a value of 5) to 77
    val breakmap = ClassMap(Map(2.0 -> 77))
    val srcAST = randomSourceAST
    val tms = Interpreter.interpretTMS(
      ast = srcAST.classify(breakmap),
      sourceMapping = Map(srcAST.id -> tileSource(4)),
      overrides = Map.empty,
      tileSource = goodSource
    )

    val ret = tms(0, 1, 1)
    val op = Await.result(ret, 10.seconds) match {
      case Valid(lazyTile) =>
        val maybeTile = lazyTile.evaluate
        requests.length should be (1)
        assertEqual(maybeTile.get, createValueTile(256, NODATA))
      case i@Invalid(_) =>
        fail(s"$i")
    }
  }

  it("should evaluate subtraction") {
    requests = Nil
    val src1 = randomSourceAST
    val src2 = randomSourceAST
    val tms = Interpreter.interpretTMS(
      ast = src1 - src2,
      sourceMapping = Map(src1.id -> tileSource(1), src2.id -> tileSource(5)),
      overrides = Map.empty,
      tileSource = goodSource
    )
    println("Subtraction node: ", (src1 - src2).asJson.noSpaces)

    val ret = tms(0, 1, 1)
    val op = Await.result(ret, 10.seconds) match {
      case Valid(lazytile) =>
        val maybeTile = lazytile.evaluateDouble
        requests.length should be (2)
        maybeTile.get.get(0, 0) should be (1 - 5)
      case i@Invalid(_) =>
        fail(s"$i")
    }
  }

  it("should preserve order of operations while evaluating subtraction") {
    requests = Nil
    // 4, 5, 1
    val src1 = randomSourceAST
    val src2 = randomSourceAST
    val src3 = randomSourceAST
    val ast = Subtraction(List(src1, src2, src3), UUID.randomUUID(), None)
    val tms = Interpreter.interpretTMS(
      ast = ast,
      sourceMapping = Map(src1.id -> tileSource(5), src2.id -> tileSource(4), src3.id -> tileSource(1)),
      overrides = Map.empty,
      tileSource = goodSource
    )

    val ret = tms(0, 1, 1)
    val op = Await.result(ret, 10.seconds) match {
      case Valid(lazytile) =>
        val maybeTile = lazytile.evaluateDouble
        requests.length should be (ast.args.length)
        maybeTile.get.get(0, 0) should be (5 - 4 - 1)
      case i@Invalid(_) =>
        fail(s"$i")
    }
  }

  it("should evaluate division") {
    requests = Nil
    val src1 = randomSourceAST
    val src2 = randomSourceAST
    val tms = Interpreter.interpretTMS(
      ast = src1 / src2,
      sourceMapping = Map(src1.id -> tileSource(4), src2.id -> tileSource(5)),
      overrides = Map.empty,
      tileSource = goodSource
    )
    println("Division node: ", (src1 / src2).asJson.noSpaces)

    val ret = tms(0, 1, 1)
    val op = Await.result(ret, 10.seconds) match {
      case Valid(lazytile) =>
        val maybeTile = lazytile.evaluateDouble
        requests.length should be (2)
        maybeTile.get.getDouble(0, 0) should be (4.0 / 5.0)
      case i@Invalid(_) =>
        fail(s"$i")
    }
  }

  it("should preserve order of operations while evaluating division") {
    requests = Nil
    val src1 = randomSourceAST
    val src2 = randomSourceAST
    val src3 = randomSourceAST
    val ast = Division(List(src1, src2, src3), UUID.randomUUID(), None)
    val tms = Interpreter.interpretTMS(
      ast = ast,
      sourceMapping = Map(src1.id -> tileSource(1), src2.id -> tileSource(5), src3.id -> tileSource(4)),
      overrides = Map.empty,
      tileSource = goodSource
    )

    val ret = tms(0, 1, 1)
    val op = Await.result(ret, 10.seconds) match {
      case Valid(lazytile) =>
        val maybeTile = lazytile.evaluateDouble
        requests.length should be (ast.args.length)
        maybeTile.get.getDouble(0, 0) should be (1.0 / 5.0 / 4.0)
      case i@Invalid(_) =>
        fail(s"$i")
    }
  }

  it("should evaluate multiplication") {
    requests = Nil
    val src1 = randomSourceAST
    val src2 = randomSourceAST
    val tms = Interpreter.interpretTMS(
      ast = src1 * src2,
      sourceMapping = Map(src1.id -> tileSource(4), src2.id -> tileSource(5)),
      overrides = Map.empty,
      tileSource = goodSource
    )
    println("Multiplication node: ", (src1 * src2).asJson.noSpaces)

    val ret = tms(0, 1, 1)
    val op = Await.result(ret, 10.seconds) match {
      case Valid(lazytile) =>
        val maybeTile = lazytile.evaluateDouble
        requests.length should be (2)
        maybeTile.get.getDouble(0, 0) should be (4.0 * 5.0)
      case i@Invalid(_) =>
        fail(s"$i")
    }
  }

  it("should evaluate addition") {
    requests = Nil
    val src1 = randomSourceAST
    val src2 = randomSourceAST
    val tms = Interpreter.interpretTMS(
      ast = src1 + src2,
      sourceMapping = Map(src1.id -> tileSource(4), src2.id -> tileSource(5)),
      overrides = Map.empty,
      tileSource = goodSource
    )
    println("Addition node: ", (src1 + src2).asJson.noSpaces)

    val ret = tms(0, 1, 1)
    val op = Await.result(ret, 10.seconds) match {
      case Valid(lazytile) =>
        val maybeTile = lazytile.evaluateDouble
        requests.length should be (2)
        maybeTile.get.getDouble(0, 0) should be (4.0 + 5.0)
      case i@Invalid(_) =>
        fail(s"$i")
    }
  }

  it("should evaluate max") {
    requests = Nil
    val src1 = randomSourceAST
    val src2 = randomSourceAST
    val tms = Interpreter.interpretTMS(
      ast = src1.max(src2),
      sourceMapping = Map(src1.id -> tileSource(1), src2.id -> tileSource(5)),
      overrides = Map.empty,
      tileSource = goodSource
    )
    println("LocalMax node: ", (src1.max(src2)).asJson.noSpaces)

    val ret = tms(0, 1, 1)
    val op = Await.result(ret, 10.seconds) match {
      case Valid(lazytile) =>
        val maybeTile = lazytile.evaluateDouble
        requests.length should be (2)
        maybeTile.get.get(0, 0) should be (5)
      case i@Invalid(_) =>
        fail(s"$i")
    }
  }

  it("should evaluate min") {
    requests = Nil
    val src1 = randomSourceAST
    val src2 = randomSourceAST
    val tms = Interpreter.interpretTMS(
      ast = src1.min(src2),
      sourceMapping = Map(src1.id -> tileSource(1), src2.id -> tileSource(5)),
      overrides = Map.empty,
      tileSource = goodSource
    )
    println("LocalMin node: ", (src1.min(src2)).asJson.noSpaces)

    val ret = tms(0, 1, 1)
    val op = Await.result(ret, 10.seconds) match {
      case Valid(lazytile) =>
        val maybeTile = lazytile.evaluateDouble
        requests.length should be (2)
        maybeTile.get.get(0, 0) should be (1)
      case i@Invalid(_) =>
        fail(s"$i")
    }
  }

  it("should evaluate multiple operations (ndvi)") {
    requests = Nil
    val nir = randomSourceAST
    val red = randomSourceAST
    val ndvi = (nir - red) / (nir + red)
    val tms = Interpreter.interpretTMS(
      ast = ndvi,
      sourceMapping = Map(nir.id -> tileSource(1), red.id -> tileSource(5)),
      overrides = Map.empty,
      tileSource = goodSource
    )
    println("Simple NDVI calculation: ", ndvi.asJson.noSpaces)

    val ret = tms(0, 1, 1)
    val op = Await.result(ret, 10.seconds) match {
      case Valid(lazytile) =>
        val maybeTile = lazytile.evaluateDouble
        requests.length should be (2)
        maybeTile.get.getDouble(0, 0) should be (-4.0/6.0)
      case i@Invalid(_) =>
        fail(s"$i")
    }
  }

  it("should evaluate masking") {
    import geotrellis.proj4._
    requests = Nil
    val subExtent: Extent = Interpreter.layouts(2).mapTransform(0, 0)
    val mask = MultiPolygon(subExtent.toPolygon)

    val src = randomSourceAST
    val masking = Masking(List(src), UUID.randomUUID, None, mask)
    val tms = Interpreter.interpretTMS(
      ast = masking,
      sourceMapping = Map(src.id -> tileSource(1)),
      overrides = Map.empty,
      tileSource = goodSource
    )
    println("Simple Masking calculation: ", masking.asJson.noSpaces)

    val ret = tms(1, 0, 0)
    val op = Await.result(ret, 10.seconds) match {
      case Valid(lazytile) =>
        val maybeTile = lazytile.evaluateDouble
        requests.length should be (1)
        maybeTile.get.getDouble(10, 10) should be (1)
      case i@Invalid(_) =>
        fail(s"$i")
    }
  }

  it("should evaluate focal maximum") {
    import geotrellis.raster.mapalgebra.focal._
    requests = Nil
    val src = randomSourceAST
    val fmax = FocalMax(List(src), UUID.randomUUID, None, Square(1))
    val tms = Interpreter.interpretTMS(
      ast = fmax,
      sourceMapping = Map(src.id -> tileSource(1)),
      overrides = Map.empty,
      tileSource = ascendingSource
    )
    println("Simple focal maximum calculation: ", fmax.asJson.noSpaces)

    val ret = tms(1, 0, 0)
    val op = Await.result(ret, 10.seconds) match {
      case Valid(lazytile) =>
        val maybeTile = lazytile.evaluateDouble
        requests.length should be (1)
        val tile = maybeTile.get
        tile.get(21, 32) should be (tile.get(20, 32) + 1)
      case i@Invalid(_) =>
        fail(s"$i")
    }
  }

  it("should evaluate focal minimum") {
    import geotrellis.raster.mapalgebra.focal._
    requests = Nil
    val src = randomSourceAST
    val fmin = FocalMin(List(src), UUID.randomUUID, None, Square(1))
    val tms = Interpreter.interpretTMS(
      ast = fmin,
      sourceMapping = Map(src.id -> tileSource(1)),
      overrides = Map.empty,
      tileSource = ascendingSource
    )
    println("Simple focal minimum calculation: ", fmin.asJson.noSpaces)

    val ret = tms(1, 0, 0)
    val op = Await.result(ret, 10.seconds) match {
      case Valid(lazytile) =>
        val maybeTile = lazytile.evaluateDouble
        requests.length should be (1)
        val tile = maybeTile.get
        tile.get(21, 32) should be (tile.get(20, 32) + 1)
      case i@Invalid(_) =>
        fail(s"$i")
    }
  }
}
