package com.azavea.rf.tool.eval

import com.typesafe.scalalogging.LazyLogging
import geotrellis.raster._
import geotrellis.raster.mapalgebra.local._
import geotrellis.raster.mapalgebra.focal
import geotrellis.raster.mapalgebra.focal.Neighborhood
import geotrellis.raster.render._
import geotrellis.vector.{ Extent, MultiPolygon, Point }
import spire.syntax.cfor._

sealed trait LazyTile extends TileLike with Grid with LazyLogging {
  // TODO: Move these out into a TypeClass
  def +(other: LazyTile) = this.dualCombine(other)(Add.combine)(Add.combine)
  def -(other: LazyTile) = this.dualCombine(other)(Subtract.combine)(Subtract.combine)
  def /(other: LazyTile) = this.dualCombine(other)(Divide.combine)(Divide.combine)
  def *(other: LazyTile) = this.dualCombine(other)(Multiply.combine)(Multiply.combine)
  def **(other: LazyTile) = this.dualCombine(other)(Pow.combine)(Pow.combine)

  def ==(other: LazyTile) = this.dualCombine(other)({(a, b) => if (Equal.compare(a, b)) 1 else 0})({(a, b) => if (Equal.compare(a, b)) 1 else 0})
  def !=(other: LazyTile) = this.dualCombine(other)({(a, b) => if (Unequal.compare(a, b)) 1 else 0})({(a, b) => if (Unequal.compare(a, b)) 1 else 0})
  def >(other: LazyTile) = this.dualCombine(other)({(a, b) => if (Greater.compare(a, b)) 1 else 0})({(a, b) => if (Greater.compare(a, b)) 1 else 0})
  def >=(other: LazyTile) = this.dualCombine(other)({(a, b) => if (GreaterOrEqual.compare(a, b)) 1 else 0})({(a, b) => if (GreaterOrEqual.compare(a, b)) 1 else 0})
  def <(other: LazyTile) = this.dualCombine(other)({(a, b) => if (Less.compare(a, b)) 1 else 0})({(a, b) => if (Less.compare(a, b)) 1 else 0})
  def <=(other: LazyTile) = this.dualCombine(other)({(a, b) => if (LessOrEqual.compare(a, b)) 1 else 0})({(a, b) => if (LessOrEqual.compare(a, b)) 1 else 0})

  @SuppressWarnings(Array("ComparingFloatingPointTypes"))
  def not = this.dualMap({z: Int => if(isNoData(z)) z else if (z == 0) 1 else 0})({z => if(isNoData(z)) z else if (z == 0.0) 1 else 0})
  def and(other: LazyTile) = this.dualCombine(other)(And.combine)(And.combine)
  def or(other: LazyTile) = this.dualCombine(other)(Or.combine)(Or.combine)
  def xor(other: LazyTile) = this.dualCombine(other)(Xor.combine)(Xor.combine)

  def ceil = this.dualMap({z: Int => z})({z => math.ceil(z)})
  def floor = this.dualMap({z: Int => z})({z => math.floor(z)})
  def round = this.dualMap({z: Int => z})({z => math.round(z)})

  def defined = this.dualMap({z: Int => if(isNoData(z)) 0 else 1})({ z: Double => if(isNoData(z)) 0 else 1 })
  def undefined = this.dualMap({z: Int => if(isNoData(z)) 1 else 0})({z: Double => if(isNoData(z)) 1 else 0})

  def sqrt = this.dualMap({z: Int => if(isNoData(z) || z < 0) NODATA else math.sqrt(z).toInt})({z: Double => math.sqrt(z)})
  def log = this.dualMap({z: Int => if(isNoData(z)) z else d2i(math.log(z))})({z: Double => math.log(z)})
  def log10 = this.dualMap({z: Int => if(isNoData(z)) z else d2i(math.log10(z))})({z: Double => math.log10(z)})
  def abs = this.dualMap({z: Int => if (isNoData(z)) z else math.abs(z)})({z => if (isNoData(z)) z else math.abs(z)})
  def inverse = this.dualMap({z: Int => if(isNoData(z)) z else -z})({z => if(isNoData(z)) z else -z})

  def sin = this.dualMap({z: Int => if(isNoData(z)) z else d2i(math.sin(z))})({z => if(isNoData(z)) z else math.sin(z)})
  def cos = this.dualMap({z: Int => if(isNoData(z)) z else d2i(math.cos(z))})({z => if(isNoData(z)) z else math.cos(z)})
  def tan = this.dualMap({z: Int => if(isNoData(z)) z else d2i(math.tan(z))})({z => if(isNoData(z)) z else math.tan(z)})
  def asin = this.dualMap({z: Int => if(isNoData(z)) z else d2i(math.asin(z))})({z => if(isNoData(z)) z else math.asin(z)})
  def acos = this.dualMap({z: Int => if(isNoData(z)) z else d2i(math.acos(z))})({z => if(isNoData(z)) z else math.acos(z)})
  def atan = this.dualMap({z: Int => if(isNoData(z)) z else d2i(math.atan(z))})({z => if(isNoData(z)) z else math.atan(z)})
  def sinh = this.dualMap({z: Int => if(isNoData(z)) z else d2i(math.sinh(z))})({z => if(isNoData(z)) z else math.sinh(z)})
  def cosh = this.dualMap({z: Int => if(isNoData(z)) z else d2i(math.cosh(z))})({z => if(isNoData(z)) z else math.cosh(z)})
  def tanh = this.dualMap({z: Int => if(isNoData(z)) z else d2i(math.tanh(z))})({z => if(isNoData(z)) z else math.tanh(z)})
  def atan2(other: LazyTile) = this.dualCombine(other)({(z1, z2) => d2i(math.atan2(z1, z2))})({(z1, z2) => math.atan2(z1, z2)})

  def max(other: LazyTile) = this.dualCombine(other)(Max.combine)(Max.combine)
  def min(other: LazyTile) = this.dualCombine(other)(Min.combine)(Min.combine)
  def classify(breaks: BreakMap[Double, Int]) = this.classification({ i => breaks(i) })
  def mask(extent: Extent, mask: MultiPolygon) = LazyTile.Masking(this, extent, mask)
  def focalMax(neighborhood: Neighborhood, gridbounds: Option[GridBounds]) = LazyTile.FocalMax(this, neighborhood, gridbounds)
  def focalMin(neighborhood: Neighborhood, gridbounds: Option[GridBounds]) = LazyTile.FocalMin(this, neighborhood, gridbounds)
  def focalMean(neighborhood: Neighborhood, gridbounds: Option[GridBounds]) = LazyTile.FocalMean(this, neighborhood, gridbounds)
  def focalMedian(neighborhood: Neighborhood, gridbounds: Option[GridBounds]) = LazyTile.FocalMedian(this, neighborhood, gridbounds)
  def focalMode(neighborhood: Neighborhood, gridbounds: Option[GridBounds]) = LazyTile.FocalMode(this, neighborhood, gridbounds)
  def focalSum(neighborhood: Neighborhood, gridbounds: Option[GridBounds]) = LazyTile.FocalSum(this, neighborhood, gridbounds)
  def focalStdDev(neighborhood: Neighborhood, gridbounds: Option[GridBounds]) = LazyTile.FocalStdDev(this, neighborhood, gridbounds)

  def left: LazyTile
  def right: LazyTile
  def cols: Int
  def rows: Int
  def get(col: Int, row: Int): Int
  def getDouble(col: Int, row: Int): Double
  def fullyBound: Boolean

  def classification(f: Double => Int) =
    LazyTile.Classify(this, f)

  def map(f: Int => Int): LazyTile.Tree =
    LazyTile.MapInt(this, f)

  def mapDouble(f: Double => Double): LazyTile.Tree =
    LazyTile.MapDouble(this, f)

  def combine(other: LazyTile)(f: (Int, Int) => Int): LazyTile.Tree =
    LazyTile.CombineInt(this, other, f)

  def combineDouble(other: LazyTile)(f: (Double, Double) => Double): LazyTile.Tree =
    LazyTile.CombineDouble(this, other, f)

  def dualCombine(other: LazyTile)(f: (Int, Int) => Int)(g: (Double, Double) => Double): LazyTile.Tree =
    LazyTile.DualCombine(this, other, f, g)

  def dualMap(f: Int => Int)(g: Double => Double): LazyTile.Tree =
    LazyTile.DualMap(this, f, g)

  def mapIntMapper(mapper: IntTileMapper): LazyTile.Tree =
    LazyTile.IntMapper(this, mapper)

  def mapDoubleMapper(mapper: DoubleTileMapper): LazyTile.Tree =
    LazyTile.DoubleMapper(this, mapper)

  def bind(args: Map[LazyTile.Var, LazyTile]): LazyTile

  def evaluateAs(ct: CellType): Option[Tile] =
    this match {
      case LazyTile.Empty =>
        None
      case _ =>
        val mutableOutput = ArrayTile.empty(ct, cols, rows)
        if (ct.isFloatingPoint) {
          cfor(0)(_ < rows, _ + 1) { row =>
            cfor(0)(_ < cols, _ + 1) { col =>
              mutableOutput.setDouble(col, row, getDouble(col, row))
            }
          }
        } else {
          cfor(0)(_ < rows, _ + 1) { row =>
            cfor(0)(_ < cols, _ + 1) { col =>
              mutableOutput.set(col, row, get(col, row))
            }
          }
        }
        Some(mutableOutput)
    }

  def evaluate = evaluateAs(IntConstantNoDataCellType)

  def evaluateDouble = evaluateAs(DoubleConstantNoDataCellType)

}

@SuppressWarnings(Array("EitherGet"))
object LazyTile {
  implicit def tileToLazyTile(tile: Tile): LazyTile = Bound(tile)

  def apply(name: Symbol): LazyTile = Var(name)
  def apply(name: Symbol, band: Int): LazyTile = Var(name, Some(band))
  def apply(identifier: String): LazyTile = Var(identifier)
  def apply(tile: Tile): LazyTile = Bound(tile)

  /** Nil allows us to have a single trait for both binary and unary ops */
  case object Nil extends LazyTile {
    def left = this
    def right = this
    def cols = 0
    def rows = 0
    def get(col: Int, row: Int): Int = NODATA
    def getDouble(col: Int, row: Int): Double = NODATA
    def fullyBound = true
    def bind(args: Map[Var, LazyTile]): LazyTile = this
  }

  /** An LazyTile.Tree has a left and right. The terminal node will have Nil on the left and right */
  trait Tree extends LazyTile {
    def cols = left.cols
    def rows = left.rows
    def fullyBound: Boolean = left.fullyBound && right.fullyBound
  }

  // TODO: Think about assurances we can offer (and how) related to col/row of a tile
  // This strategy makes it hard to have unbound LazyTiles:
  //    require(left.dimensions == right.dimensions, "Cannot combine ops with different dimensions: " +
  //      s"${left.dimensions} does not match ${right.dimensions}")

  object Var {
    val VarRx = """([a-zA-Z_\d-]+)(\[(\d+)\])?""".r
    def apply(identifier: String): Var = {
      try {
        val VarRx(name, _, band) = identifier
        Var(Symbol(name), Option(band).map(_.toInt))
      } catch {
        case e: MatchError =>
          throw new IllegalArgumentException(s"`$identifier` is not a valid identifer")
      }
    }

    def apply(name: Symbol, band: Int): Var = Var(name, Some(band))
  }

  case class Var(name: Symbol, band: Option[Int] = None) extends Tree {
    def get(col: Int, row: Int): Int = NODATA
    def getDouble(col: Int, row: Int): Double = NODATA
    def left = LazyTile.Nil
    def right = LazyTile.Nil
    override def fullyBound: Boolean = false
    def bind(args: Map[Var, LazyTile]): LazyTile = {
      logger.debug(s"Attempting to bind a variable: (${this}) with these args: ($args)")
      args.find(_._1.name == this.name).map(_._2) match {
        case Some(LazyTile.Var(symbol, band)) =>
          LazyTile.Var(symbol, band)
        case Some(Unbound(Some(mbtile))) =>
          Bound(mbtile.band(band.getOrElse(0)))
        case Some(Unbound(None)) =>
          LazyTile.Empty
        case None =>
          logger.debug("Unable to find match for variable")
          this
        case _ =>
          logger.info(s"Unexpected match found while binding variable ($this) with args: ($args)")
          this
      }
    }
  }

  /** This object represents a possibly returned tile which is to be bound as a parameter to an
    *  [[LazyTile]] expression tree. It can either resolve (and provide a tile) or not (and provide NODATA)
    */
  case class Unbound(maybeTile: Option[MultibandTile]) extends Tree {
    def get(col: Int, row: Int): Int = NODATA
    def getDouble(col: Int, row: Int): Double = NODATA
    def left = LazyTile.Nil
    def right = LazyTile.Nil
    override def fullyBound: Boolean = false
    def bind(args: Map[Var, LazyTile]): LazyTile = this
  }

  /** This object represents cases in which the catalog returns no tiles at the given coords */
  case object Empty extends Tree {
    def get(col: Int, row: Int): Int = NODATA
    def getDouble(col: Int, row: Int): Double = doubleNODATA
    def left = LazyTile.Nil
    def right = LazyTile.Nil
    def bind(args: Map[Var, LazyTile]): LazyTile = this
  }

  /** This object represents tile data sources */
  case class Bound(tile: Tile) extends Tree {
    override def cols: Int = tile.cols
    override def rows: Int = tile.rows
    def get(col: Int, row: Int): Int = tile.get(col,row)
    def getDouble(col: Int, row: Int): Double = tile.getDouble(col, row)
    def left = LazyTile.Nil
    def right = LazyTile.Nil
    def bind(args: Map[Var, LazyTile]): LazyTile = this
  }

  case class Classify(left: LazyTile, f: Double => Int) extends Tree {
    def get(col: Int, row: Int) = f(left.getDouble(col, row))
    def getDouble(col: Int, row: Int) = i2d(get(col, row))
    def right = LazyTile.Nil
    def bind(args: Map[Var, LazyTile]): LazyTile =
      Classify(left.bind(args), f)
  }

  case class Masking(left: LazyTile, extent: Extent, mask: MultiPolygon) extends Tree {
    lazy val cellMask: Tile = {
      val masky = ArrayTile.empty(BitCellType, this.cols, this.rows)

      RasterExtent(extent, this.cols, this.rows)
        .foreach(mask)({ (col, row) => masky.set(col, row, 1) })

      masky
    }

    /** Perform the NODATA checks ahead of time, in case the underlying Tile
      * is sparse. This will then only check for Mask intersection if the value to
      * give back could be something other than NODATA.
      */
    def get(col: Int, row: Int): Int = {
      val v: Int = left.get(col, row)

      if (isNoData(v)) v else if (cellMask.get(col, row) == 1) v else NODATA
    }
    def getDouble(col: Int, row: Int): Double = {
      val v: Double = left.getDouble(col, row)

      if (isNoData(v)) v else if (cellMask.get(col, row) == 1) v else Double.NaN
    }
    def right = LazyTile.Nil
    def bind(args: Map[Var, LazyTile]): LazyTile =
      Masking(left.bind(args), extent, mask)
  }

  case class MapInt(left: LazyTile, f: Int => Int) extends Tree {
    def get(col: Int, row: Int) = f(left.get(col, row))
    def getDouble(col: Int, row: Int) = i2d(get(col, row))
    def right = LazyTile.Nil
    def bind(args: Map[Var, LazyTile]): LazyTile =
      MapInt(left.bind(args), f)
  }

  case class MapDouble(left: LazyTile, f: Double => Double) extends Tree {
    def get(col: Int, row: Int) = d2i(f(left.getDouble(col, row)))
    def getDouble(col: Int, row: Int) = f(left.getDouble(col, row))
    def right = LazyTile.Nil
    def bind(args: Map[Var, LazyTile]): LazyTile =
      MapDouble(left.bind(args), f)
  }

  case class DualMap(left: LazyTile, f: Int => Int, g: Double => Double) extends Tree {
    def get(col: Int, row: Int) = f(left.get(col, row))
    def getDouble(col: Int, row: Int) = g(left.getDouble(col, row))
    def right = LazyTile.Nil
    def bind(args: Map[Var, LazyTile]): LazyTile =
      DualMap(left.bind(args), f, g)
  }

  case class IntMapper(left: LazyTile, mapper: IntTileMapper) extends Tree {
    def get(col: Int, row: Int) = mapper(col, row, left.get(col, row))
    def getDouble(col: Int, row: Int) = i2d(mapper(col, row, left.get(col, row)))
    def right = LazyTile.Nil
    def bind(args: Map[Var, LazyTile]): LazyTile =
      IntMapper(left.bind(args), mapper)
  }

  case class DoubleMapper(left: LazyTile, mapper: DoubleTileMapper) extends Tree {
    def get(col: Int, row: Int) = d2i(mapper(col, row, left.getDouble(col, row)))
    def getDouble(col: Int, row: Int) = mapper(col, row, left.getDouble(col, row))
    def right = LazyTile.Nil
    def bind(args: Map[Var, LazyTile]): LazyTile =
      DoubleMapper(left.bind(args), mapper)
  }

  case class CombineInt(left: LazyTile, right: LazyTile, f: (Int, Int) => Int) extends Tree {
    def get(col: Int, row: Int) = f(left.get(col, row), right.get(col, row))
    def getDouble(col: Int, row: Int) = i2d(f(left.get(col, row), right.get(col, row)))
    def bind(args: Map[Var, LazyTile]): LazyTile =
      CombineInt(left.bind(args), right.bind(args), f)
  }

  case class CombineDouble(left: LazyTile, right: LazyTile, f: (Double, Double) => Double) extends Tree {
    def get(col: Int, row: Int) = d2i(f(left.getDouble(col, row), right.getDouble(col, row)))
    def getDouble(col: Int, row: Int) = f(left.getDouble(col, row), right.getDouble(col, row))
    def bind(args: Map[Var, LazyTile]): LazyTile =
      CombineDouble(left.bind(args), right.bind(args), f)
  }

  case class DualCombine(left: LazyTile, right: LazyTile, f: (Int, Int) => Int, g: (Double, Double) => Double) extends Tree {
    def get(col: Int, row: Int) = f(left.get(col, row), right.get(col, row))
    def getDouble(col: Int, row: Int) = g(left.getDouble(col, row), right.getDouble(col, row))
    def bind(args: Map[Var, LazyTile]): LazyTile =
      DualCombine(left.bind(args), right.bind(args), f, g)
  }

  case class Constant(value: Double) extends Tree {
    def get(col: Int, row: Int) = value.toInt
    def getDouble(col: Int, row: Int) = value
    def left = LazyTile.Nil
    def right = LazyTile.Nil
    // These overrides allows us to evaluate a tile which consists of this value
    override def cols: Int = 256
    override def rows: Int = 256
    def bind(args: Map[Var, LazyTile]): LazyTile =
      this
  }

  trait FocalOperation extends Tree {
    val gridbounds: Option[GridBounds]
    override def cols: Int = gridbounds.map(_.width).getOrElse(left.cols)
    override def rows: Int = gridbounds.map(_.height).getOrElse(left.rows)
    def right = LazyTile.Nil
    def intTile: Tile
    def dblTile: Tile

    def get(col: Int, row: Int) = intTile.get(col, row)
    def getDouble(col: Int, row: Int) = dblTile.getDouble(col, row)
    def bind(args: Map[Var, LazyTile]): LazyTile = this
  }

  @SuppressWarnings(Array("OptionGet"))
  case class FocalMax(left: LazyTile, n: Neighborhood, gridbounds: Option[GridBounds]) extends FocalOperation {
    lazy val intTile = focal.Max(left.evaluate.get, n, gridbounds)
    lazy val dblTile = focal.Max(left.evaluateDouble.get, n, gridbounds)
  }

  @SuppressWarnings(Array("OptionGet"))
  case class FocalMin(left: LazyTile, n: Neighborhood, gridbounds: Option[GridBounds]) extends FocalOperation {
    lazy val intTile = focal.Min(left.evaluate.get, n, gridbounds)
    lazy val dblTile = focal.Min(left.evaluateDouble.get, n, gridbounds)
  }

  @SuppressWarnings(Array("OptionGet"))
  case class FocalMean(left: LazyTile, n: Neighborhood, gridbounds: Option[GridBounds]) extends FocalOperation {
    lazy val intTile = focal.Mean(left.evaluate.get, n, gridbounds)
    lazy val dblTile = focal.Mean(left.evaluateDouble.get, n, gridbounds)
  }

  @SuppressWarnings(Array("OptionGet"))
  case class FocalMedian(left: LazyTile, n: Neighborhood, gridbounds: Option[GridBounds]) extends FocalOperation {
    lazy val intTile = focal.Median(left.evaluate.get, n, gridbounds)
    lazy val dblTile = focal.Median(left.evaluateDouble.get, n, gridbounds)
  }

  @SuppressWarnings(Array("OptionGet"))
  case class FocalMode(left: LazyTile, n: Neighborhood, gridbounds: Option[GridBounds]) extends FocalOperation {
    lazy val intTile = focal.Mode(left.evaluate.get, n, gridbounds)
    lazy val dblTile = focal.Mode(left.evaluateDouble.get, n, gridbounds)
  }

  @SuppressWarnings(Array("OptionGet"))
  case class FocalSum(left: LazyTile, n: Neighborhood, gridbounds: Option[GridBounds]) extends FocalOperation {
    lazy val intTile = focal.Sum(left.evaluate.get, n, gridbounds)
    lazy val dblTile = focal.Sum(left.evaluateDouble.get, n, gridbounds)
  }

  @SuppressWarnings(Array("OptionGet"))
  case class FocalStdDev(left: LazyTile, n: Neighborhood, gridbounds: Option[GridBounds]) extends FocalOperation {
    lazy val intTile = focal.StandardDeviation(left.evaluate.get, n, gridbounds)
    lazy val dblTile = focal.StandardDeviation(left.evaluateDouble.get, n, gridbounds)
  }

}
