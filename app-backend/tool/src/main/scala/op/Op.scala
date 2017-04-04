package geotrellis.raster.op

import com.azavea.rf.tool.ast._

import geotrellis.raster._
import geotrellis.raster.render._
import spire.syntax.cfor._
import com.typesafe.scalalogging.LazyLogging
import cats.data.Validated
import Validated._


sealed trait Op extends TileLike with Grid with LazyLogging {
  // TODO: Move these out into a TypeClass
  def +(x: Int) = this.map((_: Int) + x)
  def +(x: Double) = this.mapDouble(_ + x)
  def +(other: Op) = this.dualCombine(other)(_ + _)(_ + _)
  def -(x: Int) = this.map((_: Int) - x)
  def -(x: Double) = this.mapDouble(_ - x)
  def -(other: Op) = this.dualCombine(other)(_ - _)(_ - _)
  def /(x: Int) = this.map((_: Int) / x)
  def /(x: Double) = this.mapDouble(_ / x)
  def /(other: Op) = this.dualCombine(other)(_ / _)(_ / _)
  def *(x: Int) = this.map((_: Int) * x)
  def *(x: Double) = this.mapDouble(_ * x)
  def *(other: Op) = this.dualCombine(other)(_ * _)(_ * _)
  def classify(breaks: BreakMap[Double, Int]) = this.classification({ i => breaks(i) })

  def left: Op
  def right: Op
  def cols: Int
  def rows: Int
  def get(col: Int, row: Int): Int
  def getDouble(col: Int, row: Int): Double
  def fullyBound: Boolean

  def classification(f: Double => Int) =
    Op.Classify(this, f)

  def map(f: Int => Int): Op.Tree =
    Op.MapInt(this, f)

  def mapDouble(f: Double => Double): Op.Tree =
    Op.MapDouble(this, f)

  def combine(other: Op)(f: (Int, Int) => Int): Op.Tree =
    Op.CombineInt(this, other, f)

  def combineDouble(other: Op)(f: (Double, Double) => Double): Op.Tree =
    Op.CombineDouble(this, other, f)

  def dualCombine(other: Op)(f: (Int, Int) => Int)(g: (Double, Double) => Double): Op.Tree =
    Op.DualCombine(this, other, f, g)

  def mapIntMapper(mapper: IntTileMapper): Op.Tree =
    Op.IntMapper(this, mapper)

  def mapDoubleMapper(mapper: DoubleTileMapper): Op.Tree =
    Op.DoubleMapper(this, mapper)

  def bind(args: Map[Op.Var, Op]): Op

  def toTile(ct: CellType): Option[Tile] =
    this match {
      case Op.Empty =>
        None
      case _ =>
        val mutableOutput = ArrayTile.empty(ct, cols, rows)
        if (ct.isFloatingPoint) {
          cfor(0)(_ < rows, _ + 1) { row =>
            cfor(0)(_ < cols, _ + 1) { col =>
              Some(mutableOutput.setDouble(col, row, getDouble(col, row)))
            }
          }
        } else {
          cfor(0)(_ < rows, _ + 1) { row =>
            cfor(0)(_ < cols, _ + 1) { col =>
              Some(mutableOutput.set(col, row, get(col, row)))
            }
          }
        }
        Some(mutableOutput)
    }
}

object Op {
  implicit def tileToOp(tile: Tile): Op = Bound(tile)

  def apply(name: Symbol): Op = Var(name)
  def apply(name: Symbol, band: Int): Op = Var(name, Some(band))
  def apply(identifier: String): Op = Var(identifier)
  def apply(tile: Tile): Op = Bound(tile)

  /** Nil allows us to have a single trait for both binary and unary ops */
  case object Nil extends Op {
    def left = this
    def right = this
    def cols = 0
    def rows = 0
    def get(col: Int, row: Int): Int = NODATA
    def getDouble(col: Int, row: Int): Double = NODATA
    def fullyBound = true
    def bind(args: Map[Var, Op]): Op = this
  }

  /** An Op.Tree has a left and right. The terminal node will have Nil on the left and right */
  trait Tree extends Op {
    def cols = left.cols
    def rows = left.rows
    def fullyBound: Boolean = left.fullyBound && right.fullyBound
  }

  // TODO: Think about assurances we can offer (and how) related to col/row of a tile
  // This strategy makes it hard to have unbound Ops:
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
    def left = Op.Nil
    def right = Op.Nil
    override def fullyBound: Boolean = false
    def bind(args: Map[Var, Op]): Op = {
      logger.debug(s"Attempting to bind a variable: (${this}) with these args: ($args)")
      args.find(_._1.name == this.name).map(_._2) match {
        case Some(Op.Var(symbol, band)) =>
          Op.Var(symbol, band)
        case Some(Unbound(Some(mbtile))) =>
          Bound(mbtile.bands(band.getOrElse(0)))
        case Some(Unbound(None)) =>
          Op.Empty
        case None =>
          logger.debug(s"Unable to find match for variable")
          this
        case _ =>
          logger.info(s"Unexpected match found while binding variable ($this) with args: ($args)")
          this
      }
    }
  }

  /** This object represents a possibly returned tile which is to be bound as a parameter to an
    *  [[Op]] expression tree. It can either resolve (and provide a tile) or not (and provide NODATA)
    */
  case class Unbound(maybeTile: Option[MultibandTile]) extends Tree {
    def get(col: Int, row: Int): Int = NODATA
    def getDouble(col: Int, row: Int): Double = NODATA
    def left = Op.Nil
    def right = Op.Nil
    override def fullyBound: Boolean = false
    def bind(args: Map[Var, Op]): Op = this
  }

  /** This object represents cases in which the catalog returns no tiles at the given coords */
  case object Empty extends Tree {
    def get(col: Int, row: Int): Int = NODATA
    def getDouble(col: Int, row: Int): Double = doubleNODATA
    def left = Op.Nil
    def right = Op.Nil
    def bind(args: Map[Var, Op]): Op = this
  }

  /** This object represents tile data sources */
  case class Bound(tile: Tile) extends Tree {
    override def cols: Int = tile.cols
    override def rows: Int = tile.rows
    def get(col: Int, row: Int): Int = tile.get(col, row)
    def getDouble(col: Int, row: Int): Double = tile.getDouble(col, row)
    def left = Op.Nil
    def right = Op.Nil
    def bind(args: Map[Var, Op]): Op = this
  }

  case class Classify(left: Op, f: Double => Int) extends Tree {
    def get(col: Int, row: Int) = f(left.getDouble(col, row))
    def getDouble(col: Int, row: Int) = i2d(get(col, row))
    def right = Op.Nil
    def bind(args: Map[Var, Op]): Op =
      Classify(left.bind(args), f)
  }

  case class MapInt(left: Op, f: Int => Int) extends Tree {
    def get(col: Int, row: Int) = f(left.get(col, row))
    def getDouble(col: Int, row: Int) = i2d(get(col, row))
    def right = Op.Nil
    def bind(args: Map[Var, Op]): Op =
      MapInt(left.bind(args), f)
  }

  case class MapDouble(left: Op, f: Double => Double) extends Tree {
    def get(col: Int, row: Int) = d2i(f(left.getDouble(col, row)))
    def getDouble(col: Int, row: Int) = f(left.getDouble(col, row))
    def right = Op.Nil
    def bind(args: Map[Var, Op]): Op =
      MapDouble(left.bind(args), f)
  }

  case class IntMapper(left: Op, mapper: IntTileMapper) extends Tree {
    def get(col: Int, row: Int) = mapper(col, row, left.get(col, row))
    def getDouble(col: Int, row: Int) = i2d(mapper(col, row, left.get(col, row)))
    def right = Op.Nil
    def bind(args: Map[Var, Op]): Op =
      IntMapper(left.bind(args), mapper)
  }

  case class DoubleMapper(left: Op, mapper: DoubleTileMapper) extends Tree {
    def get(col: Int, row: Int) = d2i(mapper(col, row, left.getDouble(col, row)))
    def getDouble(col: Int, row: Int) = mapper(col, row, left.getDouble(col, row))
    def right = Op.Nil
    def bind(args: Map[Var, Op]): Op =
      DoubleMapper(left.bind(args), mapper)
  }

  case class CombineInt(left: Op, right: Op, f: (Int, Int) => Int) extends Tree {
    def get(col: Int, row: Int) = f(left.get(col, row), right.get(col, row))
    def getDouble(col: Int, row: Int) = i2d(f(left.get(col, row), right.get(col, row)))
    def bind(args: Map[Var, Op]): Op =
      CombineInt(left.bind(args), right.bind(args), f)
  }

  case class CombineDouble(left: Op, right: Op, f: (Double, Double) => Double) extends Tree {
    def get(col: Int, row: Int) = d2i(f(left.getDouble(col, row), right.getDouble(col, row)))
    def getDouble(col: Int, row: Int) = f(left.getDouble(col, row), right.getDouble(col, row))
    def bind(args: Map[Var, Op]): Op =
      CombineDouble(left.bind(args), right.bind(args), f)
  }

  case class DualCombine(left: Op, right: Op, f: (Int, Int) => Int, g: (Double, Double) => Double) extends Tree {
    def get(col: Int, row: Int) = f(left.get(col, row), right.get(col, row))
    def getDouble(col: Int, row: Int) = g(left.getDouble(col, row), right.getDouble(col, row))
    def bind(args: Map[Var, Op]): Op =
      DualCombine(left.bind(args), right.bind(args), f, g)
  }
}
