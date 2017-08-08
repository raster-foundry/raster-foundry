package com.azavea.rf.tool.ast

import io.circe.generic.JsonCodec
import cats.implicits._

import geotrellis.vector.MultiPolygon
import geotrellis.raster.mapalgebra.focal._

import java.util.UUID


/** The ur-type for a recursive representation of MapAlgebra operations */
sealed trait MapAlgebraAST extends Product with Serializable {
  def id: UUID
  def args: List[MapAlgebraAST]
  def metadata: Option[NodeMetadata]
  def find(id: UUID): Option[MapAlgebraAST]
  def sources: Seq[MapAlgebraAST.MapAlgebraLeaf]
  def substitute(substitutions: Map[UUID, MapAlgebraAST]): Option[MapAlgebraAST]
  def bufferedSources(buffered: Boolean = false): Set[UUID] = {
    val bufferList = this match {
      case f: MapAlgebraAST.FocalOperation => f.args.flatMap(_.bufferedSources(true))
      case op: MapAlgebraAST.Operation => op.args.flatMap(_.bufferedSources(buffered))
      case MapAlgebraAST.Source(id, _) => if (buffered) List(id) else List()
      case leaf: MapAlgebraAST.MapAlgebraLeaf => List()
      case _ => List()
    }
    bufferList.toSet
  }
}

object MapAlgebraAST {
  /** Map Algebra operations (nodes in this tree) */
  sealed trait Operation extends MapAlgebraAST with Serializable {

    val symbol: String

    @SuppressWarnings(Array("TraversableHead"))
    def find(id: UUID): Option[MapAlgebraAST] =
      if (this.id == id)
        Some(this)
      else {
        val matches = args.flatMap(_.find(id))
        matches.headOption
      }

    def sources: Seq[MapAlgebraAST.MapAlgebraLeaf] = args.flatMap(_.sources).distinct

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST

    def substitute(substitutions: Map[UUID, MapAlgebraAST]): Option[MapAlgebraAST] = {
      val updatedArgs: Option[List[MapAlgebraAST]] = this.args.map({ arg =>
        arg.substitute(substitutions)
      }).sequence

      updatedArgs.map({ newArgs => this.withArgs(newArgs) })
    }
  }

  /** Operations which should only have one argument. */
  case class Addition(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata]) extends Operation {
  val symbol = "+"

  def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = copy(args = newArgs)
}

  case class Subtraction(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata]) extends Operation {
    val symbol = "-"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = copy(args = newArgs)
  }

  case class Multiplication(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata]) extends Operation {
    val symbol = "*"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = copy(args = newArgs)
  }

  case class Division(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata]) extends Operation {
    val symbol = "/"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = copy(args = newArgs)
  }

  case class Max(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata]) extends Operation {
    val symbol = "max"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = copy(args = newArgs)
  }

  case class Min(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata]) extends Operation {
    val symbol = "min"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = copy(args = newArgs)
  }

  case class Equality(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata]) extends Operation {
    val symbol = "=="

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = copy(args = newArgs)
  }

  case class Inequality(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata]) extends Operation {
    val symbol = "!="

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = copy(args = newArgs)
  }

  case class Greater(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata]) extends Operation {
    val symbol = ">"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = copy(args = newArgs)
  }

  case class GreaterOrEqual(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata]) extends Operation {
    val symbol = ">="

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = copy(args = newArgs)
  }

  case class Less(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata]) extends Operation {
    val symbol = "<"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = copy(args = newArgs)
  }

  case class LessOrEqual(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata]) extends Operation {
    val symbol = "<="

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = copy(args = newArgs)
  }

  case class And(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata]) extends Operation {
    val symbol = "and"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = copy(args = newArgs)
  }

  case class Or(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata]) extends Operation {
    val symbol = "or"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = copy(args = newArgs)
  }

  case class Xor(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata]) extends Operation {
    val symbol = "xor"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = copy(args = newArgs)
  }

  case class Pow(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata]) extends Operation {
    val symbol = "^"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = copy(args = newArgs)
  }

  case class Atan2(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata]) extends Operation {
    val symbol = "atan2"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = copy(args = newArgs)
  }


  sealed trait UnaryOperation extends Operation with Serializable

  case class Masking(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata], mask: MultiPolygon) extends UnaryOperation {
    val symbol = "mask"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = copy(args = newArgs)
  }

  case class Classification(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata], classMap: ClassMap) extends UnaryOperation {
    val symbol = "classify"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = copy(args = newArgs)
  }

  case class IsDefined(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata]) extends UnaryOperation {
    val symbol = "isdefined"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = copy(args = newArgs)
  }

  case class IsUndefined(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata]) extends UnaryOperation {
    val symbol = "isundefined"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = copy(args = newArgs)
  }

  case class SquareRoot(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata]) extends UnaryOperation {
    val symbol = "sqrt"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = copy(args = newArgs)
  }

  case class Log(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata]) extends UnaryOperation {
    val symbol = "log"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = copy(args = newArgs)
  }

  case class Log10(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata]) extends UnaryOperation {
    val symbol = "log10"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = copy(args = newArgs)
  }

  case class Round(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata]) extends UnaryOperation {
    val symbol = "round"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = copy(args = newArgs)
  }

  case class Floor(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata]) extends UnaryOperation {
    val symbol = "floor"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = copy(args = newArgs)
  }

  case class Ceil(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata]) extends UnaryOperation {
    val symbol = "ceil"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = copy(args = newArgs)
  }

  case class NumericNegation(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata]) extends UnaryOperation {
    val symbol = "neg"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = copy(args = newArgs)
  }

  case class LogicalNegation(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata]) extends UnaryOperation {
    val symbol = "not"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = copy(args = newArgs)
  }

  case class Abs(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata]) extends UnaryOperation {
    val symbol = "abs"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = copy(args = newArgs)
  }

  case class Sin(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata]) extends UnaryOperation {
    val symbol = "sin"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = copy(args = newArgs)
  }

  case class Cos(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata]) extends UnaryOperation {
    val symbol = "cos"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = copy(args = newArgs)
  }

  case class Tan(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata]) extends UnaryOperation {
    val symbol = "tan"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = copy(args = newArgs)
  }

  case class Sinh(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata]) extends UnaryOperation {
    val symbol = "sinh"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = copy(args = newArgs)
  }

  case class Cosh(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata]) extends UnaryOperation {
    val symbol = "cosh"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = copy(args = newArgs)
  }

  case class Tanh(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata]) extends UnaryOperation {
    val symbol = "tanh"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = copy(args = newArgs)
  }

  case class Asin(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata]) extends UnaryOperation {
    val symbol = "asin"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = copy(args = newArgs)
  }

  case class Acos(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata]) extends UnaryOperation {
    val symbol = "acos"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = copy(args = newArgs)
  }

  case class Atan(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata]) extends UnaryOperation {
    val symbol = "atan"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = copy(args = newArgs)
  }

  sealed trait FocalOperation extends UnaryOperation

  case class FocalMax(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata], neighborhood: Neighborhood) extends FocalOperation {
    val symbol = "focalMax"
    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = copy(args = newArgs)
  }

  case class FocalMin(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata], neighborhood: Neighborhood) extends FocalOperation {
    val symbol = "focalMin"
    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = copy(args = newArgs)
  }

  case class FocalMean(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata], neighborhood: Neighborhood) extends FocalOperation {
    val symbol = "focalMean"
    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = copy(args = newArgs)
  }

  case class FocalMedian(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata], neighborhood: Neighborhood) extends FocalOperation {
    val symbol = "focalMedian"
    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = copy(args = newArgs)
  }

  case class FocalMode(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata], neighborhood: Neighborhood) extends FocalOperation {
    val symbol = "focalMode"
    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = copy(args = newArgs)
  }

  case class FocalSum(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata], neighborhood: Neighborhood) extends FocalOperation {
    val symbol = "focalSum"
    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = copy(args = newArgs)
  }

  case class FocalStdDev(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata], neighborhood: Neighborhood) extends FocalOperation {
    val symbol = "focalStdDev"
    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = copy(args = newArgs)
  }

  sealed trait MapAlgebraLeaf extends MapAlgebraAST {
    val `type`: String
    def args: List[MapAlgebraAST] = List.empty
    def find(id: UUID): Option[MapAlgebraAST] =
      if (this.id == id) Some(this)
      else None
  }

  @JsonCodec
  case class Constant(id: UUID, constant: Double, metadata: Option[NodeMetadata]) extends MapAlgebraLeaf {
    val `type` = "const"
    def sources: Seq[MapAlgebraAST.MapAlgebraLeaf] = List()
    def substitute(substitutions: Map[UUID, MapAlgebraAST]): Option[MapAlgebraAST] = Some(this)
  }

  /** Map Algebra sources */
  @JsonCodec
  case class Source(id: UUID, metadata: Option[NodeMetadata]) extends MapAlgebraLeaf {
    val `type` = "src"
    def sources: Seq[MapAlgebraAST.MapAlgebraLeaf] = List(this)
    def substitute(substitutions: Map[UUID, MapAlgebraAST]): Option[MapAlgebraAST] = Some(this)
  }

  case class ToolReference(id: UUID, toolId: UUID) extends MapAlgebraLeaf {
    val `type` = "ref"

    def metadata: Option[NodeMetadata] = None
    def sources: List[MapAlgebraAST.Source] = List()
    def substitute(substitutions: Map[UUID, MapAlgebraAST]): Option[MapAlgebraAST] =
      substitutions.get(toolId)
  }

}
