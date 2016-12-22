package geotrellis.raster.op

import geotrellis.raster._
import spire.syntax.cfor._

trait Op extends TileLike with Grid {
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

  def cols: Int
  def rows: Int
  def get(col: Int, row: Int): Int
  def getDouble(col: Int, row: Int): Double

  def map(f: Int => Int): Op =
    Op.MapInt(this, f)

  def mapDouble(f: Double => Double): Op =
    Op.MapDouble(this, f)

  def combine(other: Op)(f: (Int, Int) => Int): Op =
    Op.CombineInt(this, other, f)

  def combineDouble(other: Op)(f: (Double, Double) => Double): Op =
    Op.CombineDouble(this, other, f)

  def dualCombine(other: Op)(f: (Int, Int) => Int)(g: (Double, Double) => Double): Op =
    Op.DualCombine(this, other, f, g)

  def mapIntMapper(mapper: IntTileMapper) =
    Op.IntMapper(this, mapper)

  def mapDoubleMapper(mapper: DoubleTileMapper) =
    Op.DoubleMapper(this, mapper)

  def vars: OpVars = new OpVars(this, Map.empty)

  def bind(args: Map[Op.Var, Op]): Op

  def toTile(ct: CellType): Tile = {
    val output = ArrayTile.empty(ct, cols, rows)
    if (ct.isFloatingPoint) {
      cfor(0)(_ < rows, _ + 1) { row =>
        cfor(0)(_ < cols, _ + 1) { col =>
          output.setDouble(col, row, getDouble(col, row))
        }
      }
    } else {
      cfor(0)(_ < rows, _ + 1) { row =>
        cfor(0)(_ < cols, _ + 1) { col =>
          output.set(col, row, get(col, row))
        }
      }
    }
    output
  }
}

object Op {
  implicit def tileToOp(tile: Tile): Op = Bound(tile)

  def apply(name: Symbol): Op = Var(name)
  def apply(name: Symbol, band: Int): Op = Var(name, Some(band))
  def apply(identifier: String): Op = Var(identifier)
  def apply(tile: Tile): Op = Bound(tile)

  trait Unary extends Op {
    def src: Op
    def cols: Int = src.cols
    def rows: Int = src.rows
  }

  trait Binary extends Op {
    def left: Op
    def right: Op
    def cols: Int = left.cols
    def rows: Int = left.rows

    // TODO: This makes it hard to have unbound Ops
    //    require(left.dimensions == right.dimensions, "Cannot combine ops with different dimensions: " +
    //      s"${left.dimensions} does not match ${right.dimensions}")
  }

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

  case class Var(name: Symbol, band: Option[Int] = None) extends Op {
    def cols: Int = ???
    def rows: Int = ???
    def get(col: Int, row: Int): Int = ???
    def getDouble(col: Int, row: Int): Double = ???
    def bind(args: Map[Var, Op]): Op = {
      args.get(this) match {
        case Some(replacement) =>
          replacement
        case _ => this
      }
    }
  }

  case class Bound(tile: Tile) extends Op {
    def cols: Int = tile.cols
    def rows: Int = tile.rows
    def get(col: Int, row: Int): Int = tile.get(col, row)
    def getDouble(col: Int, row: Int): Double = tile.getDouble(col, row)
    def bind(args: Map[Var, Op]): Op = this
  }

  case class MapInt(src: Op, f: Int => Int) extends Unary {
    def get(col: Int, row: Int) = f(src.get(col, row))
    def getDouble(col: Int, row: Int) = i2d(f(src.get(col, row)))
    def bind(args: Map[Var, Op]): Op =
      MapInt(src.bind(args), f)
  }

  case class MapDouble(src: Op, f: Double => Double) extends Unary {
    def get(col: Int, row: Int) = d2i(f(src.getDouble(col, row)))
    def getDouble(col: Int, row: Int) = f(src.getDouble(col, row))
    def bind(args: Map[Var, Op]): Op =
      MapDouble(src.bind(args), f)
  }

  case class IntMapper(src: Op, mapper: IntTileMapper) extends Unary {
    def get(col: Int, row: Int) = mapper(col, row, src.get(col, row))
    def getDouble(col: Int, row: Int) = i2d(mapper(col, row, src.get(col, row)))
    def bind(args: Map[Var, Op]): Op =
      IntMapper(src.bind(args), mapper)
  }

  case class DoubleMapper(src: Op, mapper: DoubleTileMapper) extends Unary {
    def get(col: Int, row: Int) = d2i(mapper(col, row, src.getDouble(col, row)))
    def getDouble(col: Int, row: Int) = mapper(col, row, src.getDouble(col, row))
    def bind(args: Map[Var, Op]): Op =
      DoubleMapper(src.bind(args), mapper)
  }

  case class CombineInt(left: Op, right: Op, f: (Int, Int) => Int) extends Binary {
    def get(col: Int, row: Int) = f(left.get(col, row), right.get(col, row))
    def getDouble(col: Int, row: Int) = i2d(f(left.get(col, row), right.get(col, row)))
    def bind(args: Map[Var, Op]): Op =
      CombineInt(left.bind(args), right.bind(args), f)
  }

  case class CombineDouble(left: Op, right: Op, f: (Double, Double) => Double) extends Binary {
    def get(col: Int, row: Int) = d2i(f(left.getDouble(col, row), right.getDouble(col, row)))
    def getDouble(col: Int, row: Int) = f(left.getDouble(col, row), right.getDouble(col, row))
    def bind(args: Map[Var, Op]): Op =
      CombineDouble(left.bind(args), right.bind(args), f)
  }

  case class DualCombine(left: Op, right: Op, f: (Int, Int) => Int, g: (Double, Double) => Double) extends Binary {
    def get(col: Int, row: Int) = f(left.get(col, row), right.get(col, row))
    def getDouble(col: Int, row: Int) = g(left.getDouble(col, row), right.getDouble(col, row))
    def bind(args: Map[Var, Op]): Op =
      DualCombine(left.bind(args), right.bind(args), f, g)
  }
}
