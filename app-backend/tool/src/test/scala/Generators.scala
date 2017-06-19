package com.azavea.rf.tool

import com.azavea.rf.tool.ast._
import com.azavea.rf.tool.eval._
import com.azavea.rf.tool.params._

import org.scalacheck._
import org.scalacheck.Gen._
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Prop.forAll
import geotrellis.raster.histogram._
import geotrellis.raster.render._

import scala.util.Random
import java.util.UUID


object Generators {

  implicit lazy val arbUUID: Arbitrary[UUID] = Arbitrary(UUID.randomUUID)

  implicit lazy val arbHistogram: Arbitrary[Histogram[Double]] = Arbitrary {
    val hist = StreamingHistogram()
    1 to Random.nextInt(500) foreach(hist.countItem(_))
    hist
  }

  lazy val genClassMapOptions: Gen[ClassMap.Options] = for {
    bounds <- Gen.lzy(Gen.oneOf(LessThanOrEqualTo, LessThan, Exact, GreaterThan, GreaterThanOrEqualTo))
    ndVal <- arbitrary[Int]
    fallback <- arbitrary[Int]
  } yield ClassMap.Options(bounds, ndVal, fallback)

  lazy val genClassMap: Gen[ClassMap] = for {
    dubs <- Gen.containerOfN[List, Double](30, arbitrary[Double])
    ints <- Gen.containerOfN[List, Int](30, arbitrary[Int])
    opts <- genClassMapOptions
  } yield ClassMap(dubs.zip(ints).toMap, opts)

  lazy val genNodeMetadata: Gen[NodeMetadata] = for {
    label <- Gen.option(arbitrary[String])
    desc  <- Gen.option(arbitrary[String])
    hist  <- Gen.option(arbitrary[Histogram[Double]])
    cRamp <- Gen.lzy(Gen.option(Gen.oneOf(ColorRamps.Viridis, ColorRamps.Inferno, ColorRamps.Magma)))
    cMap  <- Gen.option(genClassMap)
  } yield NodeMetadata(label, desc, hist, cRamp, cMap)

  lazy val genRFMLRaster: Gen[RFMLRaster] = for {
    band <- arbitrary[Int]
    id <- arbitrary[UUID]
    constructor <- Gen.lzy(Gen.oneOf(SceneRaster.apply _, ProjectRaster.apply _))
  } yield constructor(id, Some(band))

  lazy val genEvalParams: Gen[EvalParams] = for {
    astIds  <- containerOfN[List, UUID](12, arbitrary[UUID])
    rasters <- containerOfN[List, RFMLRaster](12, genRFMLRaster)
  } yield EvalParams(astIds.zip(rasters).toMap)

  lazy val genSourceAST = for {
    id <- arbitrary[UUID]
    nmd <- Gen.option(genNodeMetadata)
  } yield MapAlgebraAST.Source(id, nmd)

  lazy val genConstantAST = for {
    id <- arbitrary[UUID]
    const <- arbitrary[Int]
    nmd <- Gen.option(genNodeMetadata)
  } yield MapAlgebraAST.Constant(id, const, nmd)

  def genBinaryOpAST(depth: Int) = for {
    constructor <- Gen.lzy(Gen.oneOf(
                     MapAlgebraAST.Addition.apply _,
                     MapAlgebraAST.Subtraction.apply _,
                     MapAlgebraAST.Multiplication.apply _,
                     MapAlgebraAST.Division.apply _,
                     MapAlgebraAST.Masking.apply _,
                     MapAlgebraAST.Max.apply _,
                     MapAlgebraAST.Min.apply _
                   ))
    args <- containerOfN[List, MapAlgebraAST](2, genMapAlgebraAST(depth))
    id <- arbitrary[UUID]
    nmd <- Gen.option(genNodeMetadata)
  } yield constructor(args, id, nmd)

  def genClassificationAST(depth: Int) = for {
    args <- containerOfN[List, MapAlgebraAST](1, genMapAlgebraAST(depth))
    id <- arbitrary[UUID]
    nmd <- Gen.option(genNodeMetadata)
    cmap <- genClassMap
  } yield MapAlgebraAST.Classification(args, id, nmd, cmap)

  def genOpAST(depth: Int) = Gen.frequency(
    (5 -> genBinaryOpAST(depth)),
    (1 -> genClassificationAST(depth))
  )

  /** We are forced to manually control flow in this generator to prevent stack overflows
    *  See: http://stackoverflow.com/questions/19829293/scalacheck-arbitrary-implicits-and-recursive-generators
    */
  def genMapAlgebraAST(depth: Int = 1): Gen[MapAlgebraAST] =
    if (depth >= 100) genConstantAST
    else Gen.frequency((1 -> genOpAST(depth + 1)), (1 -> genSourceAST))

}
