package com.azavea.rf.tool.ast

import geotrellis.raster.Tile
import geotrellis.raster.render._
import geotrellis.vector.Polygon
import geotrellis.vector.io._

import spray.json._

import java.util.UUID

sealed trait MapAlgebraAST extends Product with Serializable {
  def args: List[MapAlgebraAST]
  def evaluable: Boolean
  def unbound: List[MapAlgebraAST]
}

object MapAlgebraAST {
  // Map Algebra operations (node)
  abstract class Operation(val symbol: String) extends MapAlgebraAST {
    def evaluable: Boolean = (args.length >= 1) && (args.foldLeft(true)(_ && _.evaluable))
    def unbound: List[MapAlgebraAST] =
      args.foldLeft(List[MapAlgebraAST]())({ case (list, mapAlgebra) =>
        mapAlgebra.unbound ++ list
      })
  }

  case class Addition(args: List[MapAlgebraAST], id: UUID, label: Option[String])
      extends Operation("+")

  case class Subtraction(args: List[MapAlgebraAST], id: UUID, label: Option[String])
      extends Operation("-")

  case class Multiplication(args: List[MapAlgebraAST], id: UUID, label: Option[String])
      extends Operation("*")

  case class Division(args: List[MapAlgebraAST], id: UUID, label: Option[String])
      extends Operation("/")

  case class Masking(args: List[MapAlgebraAST], id: UUID, label: Option[String])
      extends Operation("mask")

  case class Reclassification(args: List[MapAlgebraAST], id: UUID, label: Option[String], classBreaks: ClassBreaks)
      extends Operation("reclassify")


  // Map Algebra sources (leaf)
  sealed trait Source[T] extends MapAlgebraAST {
    def value: Option[T]
    def args: List[MapAlgebraAST] = List.empty
    def evaluable = value.isDefined
    def unbound: List[MapAlgebraAST] = if (evaluable) List.empty else List(this)
  }

  case class RFMLRasterSource(id: UUID, label: Option[String], value: Option[RFMLRaster])
      extends Source[RFMLRaster]
  // TODO: Add other source types (or treat of them as hyperparameters - e.g. ClassBreaks, above)
  //case class VectorSource(id: UUID, label: Option[String], value: Option[Polygon])
  //    extends Source[Polygon]
  //case class DecimalSource(id: UUID, label: Option[String], value: Option[Double])
  //    extends Source[Double]
  //case class IntegralSource(id: UUID, label: Option[String], value: Option[Int])
  //    extends Source[Int]
}

