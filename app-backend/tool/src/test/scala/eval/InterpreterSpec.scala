package com.azavea.rf.tool.eval

import com.azavea.rf.tool.ast._
import com.azavea.rf.tool.eval._
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
import geotrellis.raster.mapalgebra.focal._

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import java.util.UUID


class BufferingInterpreterSpec
    extends FunSpec
       with Matchers
       with MockInterpreterResources {

  it("should evaluate simple ast") {
    val src1 = sceneRaster(4)
    val src2 = sceneRaster(5)
    val futureTile: Future[Interpreted[Tile]] =
      BufferingInterpreter.literalize(src1 - src2, constantSource, 0, 1, 1).map({ validatedAst =>
        validatedAst
          .andThen(BufferingInterpreter.interpret(_, 256)(0, 1, 1))
          .map(_.evaluate.get)
      })

    Await.result(futureTile, 10.seconds) match {
      case Valid(tile) =>
        requests.length should be (2)
        assertEqual(tile, createValueTile(256, -1).toArray)
      case i@Invalid(_) =>
        fail(s"Unable to parse tree for tile retrieval: $i")
    }
  }


  it("should not fetch unless eval paramaters are fully capable of filling out the AST") {
    requests = Nil
    val src1 = sceneRaster(4)
    val src2 = randomSourceAST
    val ret = BufferingInterpreter.literalize(src1 - src2, constantSource, 0, 1, 1).map({ validatedAst =>
      validatedAst
        .andThen(BufferingInterpreter.interpret(_, 256)(0, 1, 1))
    })

    val lt = Await.result(ret, 10.seconds)

    requests.length should be (1)
    lt should be (Invalid(NEL.of(MissingParameter(src2.id))))
  }

   /** A failed future is semantically different than successfully returned None. This test
     * allows us to simulate a failure encountered while retrieving a tile as opposed to
     * an empty region of some tile layout.
     */
  it("should deal with bad raster sourceMapping and aggregate multiple errors") {
    requests = Nil
    val src1 = sceneRaster(4)
    val src2 = randomSourceAST
    val badSource = (raster: RFMLRaster, buffer: Boolean, z: Int, x: Int, y: Int) => Future.failed { throw new Exception("Some exception") }

    val ret = BufferingInterpreter.literalize(src1 - src2, badSource, 0, 1, 1).map({ validatedAst =>
      validatedAst.andThen(BufferingInterpreter.interpret(_, 256)(0, 1, 1))
    })

    val lt = Await.result(ret, 10.seconds)

    requests should be (empty)
    lt should be (Invalid(NEL.of(MissingParameter(src2.id), RasterRetrievalError(src1))))
  }

  it("interpretPure - simple") {
    val src1 = sceneRaster(1)
    val src2 = sceneRaster(2)

    val tms = PureInterpreter.interpret[Unit](
      ast = src1 - src2,
      false
    )

    tms shouldBe Valid(())
  }

  it("interpretPure - multiple errors") {
    val ast: MapAlgebraAST = Addition(List(randomSourceAST), UUID.randomUUID, None)

    PureInterpreter.interpret[Unit](ast, false) match {
      case Invalid(nel) => nel.size shouldBe 2
      case Valid(_) => fail
    }
  }

  it("should evaluate classification (case hit)") {
    // This breakmap should convert all cells (which are set to a value of 5) to 77
    val breakmap = ClassMap(Map(6.0 -> 77))
    val src = sceneRaster(5)
    val tile = InterpreterTest.dbl(
      src.classify(breakmap),
      constantSource,
      "Local classification"
    )
    assertEqual(tile, createValueTile(256, 77))
  }

  it("should evaluate classification (case miss)") {
    // This breakmap should convert all cells (which are set to a value of 5) to NODATA
    val breakmap = ClassMap(Map(2.0 -> 77))
    val src = sceneRaster(4)
    val tile = InterpreterTest.dbl(
      src.classify(breakmap),
      constantSource,
      ""
    )
    assertEqual(tile, createValueTile(256, NODATA))
  }

  it("should evaluate subtraction") {
    val src1 = sceneRaster(1)
    val src2 = sceneRaster(5)
    val tile = InterpreterTest.dbl(
      src1 - src2,
      constantSource,
      "Local subtraction"
    )
    tile.get(0, 0) should be (1 - 5)
  }

  it("should preserve order of operations while evaluating subtraction") {
    val src1 = sceneRaster(5)
    val src2 = sceneRaster(4)
    val src3 = sceneRaster(1)
    val tile = InterpreterTest.dbl(
      Subtraction(List(src1, src2, src3), UUID.randomUUID(), None),
      constantSource,
      ""
    )
    tile.get(0, 0) should be (5 - 4 - 1)
  }

  it("should evaluate division") {
    val src1 = sceneRaster(4)
    val src2 = sceneRaster(5)
    val tile = InterpreterTest.dbl(
      src1 / src2,
      constantSource,
      "Local division"
    )
    tile.getDouble(0, 0) should be (4.0 / 5.0)
  }

  it("should preserve order of operations while evaluating division") {
    val src1 = sceneRaster(1)
    val src2 = sceneRaster(5)
    val src3 = sceneRaster(4)
    val tile = InterpreterTest.dbl(
      Division(List(src1, src2, src3), UUID.randomUUID(), None),
      constantSource,
      ""
    )
    tile.getDouble(0, 0) should be (1.0 / 5.0 / 4.0)
  }

  it("should evaluate multiplication") {
    val src1 = sceneRaster(4)
    val src2 = sceneRaster(5)
    val tile = InterpreterTest.dbl(
      src1 * src2,
      constantSource,
      "Local multiplication"
    )
    tile.getDouble(0, 0) should be (4.0 * 5.0)
  }

  it("should evaluate addition") {
    val src1 = sceneRaster(4)
    val src2 = sceneRaster(5)
    val tile = InterpreterTest.int(
      src1 + src2,
      constantSource,
      "Local addition"
    )
    tile.get(0, 0) should be (4.0 + 5.0)
  }

  it("should evaluate max") {
    val src1 = sceneRaster(1)
    val src2 = sceneRaster(5)
    val tile = InterpreterTest.int(
      src1.max(src2),
      constantSource,
      "Local maximum"
    )
    tile.get(0, 0) should be (5)
  }

  it("should evaluate min") {
    val src1 = sceneRaster(1)
    val src2 = sceneRaster(5)
    val tile = InterpreterTest.int(
      src1.min(src2),
      constantSource,
      "Local minimum"
    )
    tile.get(0, 0) should be (1)
  }

  it("should evaluate multiple operations (ndvi)") {
    val nir = sceneRaster(1)
    val red = sceneRaster(5)
    val tile = InterpreterTest.dbl(
      (nir - red) / (nir + red),
      constantSource,
      "Multiple operations (ndvi)"
    )
    tile.getDouble(0, 0) should be (-4.0/6.0)
  }

  it("should evaluate masking") {
    // We need to select a subextent which is under the z/x/y of 1/1/1
    val subExtent: Extent = BufferingInterpreter.layouts(2).mapTransform(2, 2)
    val mask = MultiPolygon(subExtent.toPolygon)

    val src = sceneRaster(1)
    val tile = InterpreterTest.dbl(
      Masking(List(src), UUID.randomUUID, None, mask),
      constantSource,
      "A masking calculation"
    )
    tile.getDouble(10, 10) should be (1)
  }

  it("should evaluate focal maximum") {
    val src = sceneRaster(1)
    val tile = InterpreterTest.dbl(
      FocalMax(List(src), UUID.randomUUID, None, Square(1)),
      ascendingSource,
      "A focal maximum calculation"
    )
    tile.get(21, 32) should be (8471)
  }

  it("should evaluate focal minimum") {
    val src = sceneRaster(1)
    val tile = InterpreterTest.dbl(
      FocalMin(List(src), UUID.randomUUID, None, Square(1)),
      ascendingSource,
      "A focal minimum calculation"
    )
    tile.get(21, 32) should be (7957)
  }

  it("should evaluate focal mean") {
    val src = sceneRaster(1)
    val tile = InterpreterTest.dbl(
      FocalMean(List(src), UUID.randomUUID, None, Square(1)),
      ascendingSource,
      "A focal mean calculation"
    )
    tile.get(21, 32) should be (8214)
  }

  it("should evaluate focal median") {
    val src = sceneRaster(1)
    val tile = InterpreterTest.dbl(
      FocalMedian(List(src), UUID.randomUUID, None, Square(1)),
      ascendingSource,
      "A focal median calculation"
    )
    tile.get(21, 32) should be (8214)
  }

  it("should evaluate focal mode") {
    val src = sceneRaster(1)
    val tile = InterpreterTest.int(
      FocalMode(List(src), UUID.randomUUID, None, Square(1)),
      ascendingSource,
      "A focal mode calculation"
    )
    tile.get(21, 32) should be (NODATA)
  }


  it("should evaluate focal sum") {
    val src = sceneRaster(1)
    val tile = InterpreterTest.int(
      FocalSum(List(src), UUID.randomUUID, None, Square(1)),
      ascendingSource,
      "A focal sum calculation"
    )
    tile.get(0, 0) should be (197385)
  }

  it("should evaluate focal stddev") {
    val src = sceneRaster(1)
    val tile = InterpreterTest.int(
      FocalStdDev(List(src), UUID.randomUUID, None, Square(1)),
      ascendingSource,
      "A focal standard deviation calculation"
    )
    tile.get(0, 0) should be (30713)
  }

  it("should evaluate pow") {
    val src1 = sceneRaster(2)
    val src2 = sceneRaster(7)
    val tile = InterpreterTest.int(
      Pow(List(src1, src2), UUID.randomUUID, None),
      constantSource,
      "Exponentiation"
    )
    assertEqual(tile, createValueTile(256, 128))
  }

  /* --- LOGICAL OPERATIONS --- */
  it("should evaluate NOT") {
    val src = sceneRaster(3)
    val tile = InterpreterTest.int(
      LogicalNegation(List(src), UUID.randomUUID, None),
      constantSource,
      "Logical Negation"
    )
    assertEqual(tile, createValueTile(256, 0))
  }

  // These operation don't work in the way I'd expect... It looks like strange behavior coming from the GT implementation
  ignore("should evaluate And") {
    val src1 = sceneRaster(3)
    val src2 = sceneRaster(4)
    val tile = InterpreterTest.int(
      And(List(src1, src2), UUID.randomUUID, None),
      moduloSource,
      "Logical AND"
    )
    tile.get(0, 6) should be (0)
    tile.get(0, 5) should be (1)
  }

  ignore("should evaluate or") {
    val src1 = sceneRaster(1)
    val src2 = sceneRaster(1)
    val tile = InterpreterTest.int(
      Or(List(src1, src2), UUID.randomUUID, None),
      ascendingSource,
      "Logical disjunction (or)"
    )
    assertEqual(tile, IntArrayTile((1 to 256 * 256).toArray, 256, 256))
  }

  ignore("should evaluate xor") {
    val src1 = sceneRaster(1)
    val src2 = sceneRaster(1)
    val tile = InterpreterTest.int(
      Xor(List(src1, src2), UUID.randomUUID, None),
      ascendingSource,
      "Logically exclusive disjunction (xor)"
    )
    assertEqual(tile, createValueTile(256, 0))
  }


  /* --- Numeric Comparison --- */
  it("should evaluate greater than") {
    val src1 = sceneRaster(2)
    val src2 = sceneRaster(1)
    val tile = InterpreterTest.int(
      Greater(List(src1, src2), UUID.randomUUID, None),
      constantSource,
      "Equality"
    )
    assertEqual(tile, createValueTile(256, 1))
  }

  it("should evaluate greater than or equal to") {
    val src1 = sceneRaster(2)
    val src2 = sceneRaster(2)
    val tile = InterpreterTest.int(
      GreaterOrEqual(List(src1, src2), UUID.randomUUID, None),
      constantSource,
      "Equality"
    )
    assertEqual(tile, createValueTile(256, 1))
  }

  it("should evaluate less than") {
    val src1 = sceneRaster(1)
    val src2 = sceneRaster(2)
    val tile = InterpreterTest.int(
      Less(List(src1, src2), UUID.randomUUID, None),
      constantSource,
      "Equality"
    )
    assertEqual(tile, createValueTile(256, 1))
  }

  it("should evaluate less than or equal to") {
    val src1 = sceneRaster(2)
    val src2 = sceneRaster(2)
    val tile = InterpreterTest.int(
      LessOrEqual(List(src1, src2), UUID.randomUUID, None),
      constantSource,
      "Equality"
    )
    assertEqual(tile, createValueTile(256, 1))
  }

  it("should evaluate equality") {
    val src1 = sceneRaster(1)
    val src2 = sceneRaster(1)
    val tile = InterpreterTest.int(
      Equality(List(src1, src2), UUID.randomUUID, None),
      constantSource,
      "Equality"
    )
    assertEqual(tile, createValueTile(256, 1))
  }

  it("should evaluate inequality") {
    val src1 = sceneRaster(1)
    val src2 = sceneRaster(2)
    val tile = InterpreterTest.int(
      Inequality(List(src1, src2), UUID.randomUUID, None),
      constantSource,
      "Inequality"
    )
    assertEqual(tile, createValueTile(256, 1))
  }

  it("should evaluate sqrt") {
    val src = sceneRaster(16)
    val tile = InterpreterTest.int(
      SquareRoot(List(src), UUID.randomUUID, None),
      constantSource,
      "Square root"
    )
    assertEqual(tile, createValueTile(256, 4))
  }

  it("should evaluate natural log") {
    val src = sceneRaster(22)
    val tile = InterpreterTest.dbl(
      Log(List(src), UUID.randomUUID, None),
      constantSource,
      "Log e"
    )
    assertEqual(tile, createValueTile(256, 3.091))
  }

  it("should evaluate log10") {
    val src = sceneRaster(32)
    val tile = InterpreterTest.dbl(
      Log10(List(src), UUID.randomUUID, None),
      constantSource,
      "Log10"
    )
    assertEqual(tile, createValueTile(256, 1.505))
  }

  it("should evaluate round") {
    val src = sceneRaster(1)
    val tile = InterpreterTest.dbl(
      Round(List(src), UUID.randomUUID, None),
      constantSource,
      "Rounding"
    )
    assertEqual(tile, createValueTile(256, 1))
  }

  it("should evaluate floor") {
    val src = sceneRaster(1)
    val tile = InterpreterTest.dbl(
      Floor(List(src), UUID.randomUUID, None),
      constantSource,
      "Floor (rounding)"
    )
    assertEqual(tile, createValueTile(256, 1))
  }

  it("should evaluate ceil") {
    val src = sceneRaster(1)
    val tile = InterpreterTest.dbl(
      Ceil(List(src), UUID.randomUUID, None),
      constantSource,
      "Ceil (rounding)"
    )
    assertEqual(tile, createValueTile(256, 1))
  }

  it("should evaluate abs") {
    val src = sceneRaster(1)
    val tile = InterpreterTest.int(
      Abs(List(src), UUID.randomUUID, None),
      constantSource,
      "Absolute value"
    )
    assertEqual(tile, createValueTile(256, 1))
  }

  it("should evaluate numeric negate") {
    val src = sceneRaster(1)
    val tile = InterpreterTest.int(
      NumericNegation(List(src), UUID.randomUUID, None),
      constantSource,
      "Numeric negation"
    )
    assertEqual(tile, createValueTile(256, -1))
  }

  it("should evaluate isDefined") {
    val src = sceneRaster(Int.MinValue)
    val tile = InterpreterTest.int(
      IsDefined(List(src), UUID.randomUUID, None),
      constantSource,
      "Mark defined cells"
    )
    assertEqual(tile, createValueTile(256, 0))
  }

  it("should evaluate isUndefined") {
    val src = sceneRaster(Int.MinValue)
    val tile = InterpreterTest.int(
      IsUndefined(List(src), UUID.randomUUID, None),
      constantSource,
      "Mark undefined cells"
    )
    assertEqual(tile, createValueTile(256, 1))
  }

  it("should evaluate sin") {
    val src = sceneRaster(1)
    val tile = InterpreterTest.dbl(
      Sin(List(src), UUID.randomUUID, None),
      constantSource,
      "Sin function"
    )
    assertEqual(tile, createValueTile(256, 0.8414))
  }

  it("should evaluate cos") {
    val src = sceneRaster(1)
    val tile = InterpreterTest.dbl(
      Cos(List(src), UUID.randomUUID, None),
      constantSource,
      "Cos function"
    )
    assertEqual(tile, createValueTile(256, 0.5403))
  }

  it("should evaluate tan") {
    val src = sceneRaster(1)
    val tile = InterpreterTest.dbl(
      Tan(List(src), UUID.randomUUID, None),
      constantSource,
      "Tan function"
    )
    assertEqual(tile, createValueTile(256, 1.5574))
  }

  it("should evaluate sinh") {
    val src = sceneRaster(1)
    val tile = InterpreterTest.dbl(
      Sinh(List(src), UUID.randomUUID, None),
      constantSource,
      "Sinh function"
    )
    assertEqual(tile, createValueTile(256, 1.1752))
  }

  it("should evaluate cosh") {
    val src = sceneRaster(1)
    val tile = InterpreterTest.dbl(
      Cosh(List(src), UUID.randomUUID, None),
      constantSource,
      "Cosh function"
    )
    assertEqual(tile, createValueTile(256, 1.54308))
  }

  it("should evaluate tanh") {
    val src = sceneRaster(1)
    val tile = InterpreterTest.dbl(
      Tanh(List(src), UUID.randomUUID, None),
      constantSource,
      "Tanh function"
    )
    assertEqual(tile, createValueTile(256, 0.7615))
  }

  it("should evaluate asin") {
    val src = sceneRaster(1)
    val tile = InterpreterTest.dbl(
      Asin(List(src), UUID.randomUUID, None),
      constantSource,
      "Asin function"
    )
    assertEqual(tile, createValueTile(256, 1.5707))
  }

  it("should evaluate acos") {
    val src = sceneRaster(1)
    val tile = InterpreterTest.dbl(
      Acos(List(src), UUID.randomUUID, None),
      constantSource,
      "Acos function"
    )
    assertEqual(tile, createValueTile(256, 0))
  }

  it("should evaluate atan") {
    val src = sceneRaster(1)
    val tile = InterpreterTest.dbl(
      Atan(List(src), UUID.randomUUID, None),
      constantSource,
      "atan function"
    )
    assertEqual(tile, createValueTile(256, 0.7853))
  }
}
