package com.rasterfoundry.common.ast

import cats.implicits._
import geotrellis.raster._
import geotrellis.raster.mapalgebra.focal._
import geotrellis.vector.MultiPolygon

import java.util.UUID

/** The ur-type for a recursive representation of MapAlgebra operations */
sealed trait MapAlgebraAST extends Product with Serializable {
  def id: UUID
  def args: List[MapAlgebraAST]
  def metadata: Option[NodeMetadata]
  def find(id: UUID): Option[MapAlgebraAST]
  def sources: Seq[MapAlgebraAST.MapAlgebraLeaf]
  def tileSources: Set[RFMLRaster] = {
    val tileList: List[RFMLRaster] = this match {
      case r: RFMLRaster                   => List(r)
      case _: MapAlgebraAST.MapAlgebraLeaf => List()
      case ast                             => ast.args.flatMap(_.tileSources)
    }
    tileList.toSet
  }
  def substitute(substitutions: Map[UUID, MapAlgebraAST]): Option[MapAlgebraAST]
  def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST
  def withMetadata(newMd: NodeMetadata): MapAlgebraAST
  def bufferedSources(buffered: Boolean = false): Set[UUID] = {
    val bufferList = this match {
      case f: MapAlgebraAST.FocalOperation =>
        f.args.flatMap(_.bufferedSources(true))
      case op: MapAlgebraAST.Operation =>
        op.args.flatMap(_.bufferedSources(buffered))
      // case MapAlgebraAST.Source(id, _)        => if (buffered) List(id) else List()
      case _ => List()
    }
    bufferList.toSet
  }
}

object MapAlgebraAST {

  /** Map Algebra operations (nodes in this tree) */
  sealed trait Operation extends MapAlgebraAST with Serializable {

    val symbol: String

    def find(id: UUID): Option[MapAlgebraAST] =
      if (this.id == id)
        Some(this)
      else {
        val matches = args.flatMap(_.find(id))
        matches.headOption
      }

    def sources: Seq[MapAlgebraAST.MapAlgebraLeaf] =
      args.flatMap(_.sources).distinct

    def substitute(
        substitutions: Map[UUID, MapAlgebraAST]): Option[MapAlgebraAST] = {
      val updatedArgs: Option[List[MapAlgebraAST]] = this.args
        .map({ arg =>
          arg.substitute(substitutions)
        })
        .sequence

      updatedArgs.map({ newArgs =>
        this.withArgs(newArgs)
      })
    }
  }

  /** Operations which should only have one argument. */
  final case class Addition(args: List[MapAlgebraAST],
                            id: UUID,
                            metadata: Option[NodeMetadata])
      extends Operation {
    val symbol = "+"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST =
      copy(args = newArgs)
    def withMetadata(newMd: NodeMetadata): MapAlgebraAST =
      copy(metadata = Some(newMd))
  }

  final case class Subtraction(args: List[MapAlgebraAST],
                               id: UUID,
                               metadata: Option[NodeMetadata])
      extends Operation {
    val symbol = "-"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST =
      copy(args = newArgs)
    def withMetadata(newMd: NodeMetadata): MapAlgebraAST =
      copy(metadata = Some(newMd))
  }

  final case class Multiplication(args: List[MapAlgebraAST],
                                  id: UUID,
                                  metadata: Option[NodeMetadata])
      extends Operation {
    val symbol = "*"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST =
      copy(args = newArgs)
    def withMetadata(newMd: NodeMetadata): MapAlgebraAST =
      copy(metadata = Some(newMd))
  }

  final case class Division(args: List[MapAlgebraAST],
                            id: UUID,
                            metadata: Option[NodeMetadata])
      extends Operation {
    val symbol = "/"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST =
      copy(args = newArgs)
    def withMetadata(newMd: NodeMetadata): MapAlgebraAST =
      copy(metadata = Some(newMd))
  }

  final case class Max(args: List[MapAlgebraAST],
                       id: UUID,
                       metadata: Option[NodeMetadata])
      extends Operation {
    val symbol = "max"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST =
      copy(args = newArgs)
    def withMetadata(newMd: NodeMetadata): MapAlgebraAST =
      copy(metadata = Some(newMd))
  }

  final case class Min(args: List[MapAlgebraAST],
                       id: UUID,
                       metadata: Option[NodeMetadata])
      extends Operation {
    val symbol = "min"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST =
      copy(args = newArgs)
    def withMetadata(newMd: NodeMetadata): MapAlgebraAST =
      copy(metadata = Some(newMd))
  }

  final case class Equality(args: List[MapAlgebraAST],
                            id: UUID,
                            metadata: Option[NodeMetadata])
      extends Operation {
    val symbol = "=="

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST =
      copy(args = newArgs)
    def withMetadata(newMd: NodeMetadata): MapAlgebraAST =
      copy(metadata = Some(newMd))
  }

  final case class Inequality(args: List[MapAlgebraAST],
                              id: UUID,
                              metadata: Option[NodeMetadata])
      extends Operation {
    val symbol = "!="

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST =
      copy(args = newArgs)
    def withMetadata(newMd: NodeMetadata): MapAlgebraAST =
      copy(metadata = Some(newMd))
  }

  final case class Greater(args: List[MapAlgebraAST],
                           id: UUID,
                           metadata: Option[NodeMetadata])
      extends Operation {
    val symbol = ">"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST =
      copy(args = newArgs)
    def withMetadata(newMd: NodeMetadata): MapAlgebraAST =
      copy(metadata = Some(newMd))
  }

  final case class GreaterOrEqual(args: List[MapAlgebraAST],
                                  id: UUID,
                                  metadata: Option[NodeMetadata])
      extends Operation {
    val symbol = ">="

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST =
      copy(args = newArgs)
    def withMetadata(newMd: NodeMetadata): MapAlgebraAST =
      copy(metadata = Some(newMd))
  }

  final case class Less(args: List[MapAlgebraAST],
                        id: UUID,
                        metadata: Option[NodeMetadata])
      extends Operation {
    val symbol = "<"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST =
      copy(args = newArgs)
    def withMetadata(newMd: NodeMetadata): MapAlgebraAST =
      copy(metadata = Some(newMd))
  }

  final case class LessOrEqual(args: List[MapAlgebraAST],
                               id: UUID,
                               metadata: Option[NodeMetadata])
      extends Operation {
    val symbol = "<="

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST =
      copy(args = newArgs)
    def withMetadata(newMd: NodeMetadata): MapAlgebraAST =
      copy(metadata = Some(newMd))
  }

  final case class And(args: List[MapAlgebraAST],
                       id: UUID,
                       metadata: Option[NodeMetadata])
      extends Operation {
    val symbol = "and"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST =
      copy(args = newArgs)
    def withMetadata(newMd: NodeMetadata): MapAlgebraAST =
      copy(metadata = Some(newMd))
  }

  final case class Or(args: List[MapAlgebraAST],
                      id: UUID,
                      metadata: Option[NodeMetadata])
      extends Operation {
    val symbol = "or"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST =
      copy(args = newArgs)
    def withMetadata(newMd: NodeMetadata): MapAlgebraAST =
      copy(metadata = Some(newMd))
  }

  final case class Xor(args: List[MapAlgebraAST],
                       id: UUID,
                       metadata: Option[NodeMetadata])
      extends Operation {
    val symbol = "xor"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST =
      copy(args = newArgs)
    def withMetadata(newMd: NodeMetadata): MapAlgebraAST =
      copy(metadata = Some(newMd))
  }

  final case class Pow(args: List[MapAlgebraAST],
                       id: UUID,
                       metadata: Option[NodeMetadata])
      extends Operation {
    val symbol = "^"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST =
      copy(args = newArgs)
    def withMetadata(newMd: NodeMetadata): MapAlgebraAST =
      copy(metadata = Some(newMd))
  }

  final case class Atan2(args: List[MapAlgebraAST],
                         id: UUID,
                         metadata: Option[NodeMetadata])
      extends Operation {
    val symbol = "atan2"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST =
      copy(args = newArgs)
    def withMetadata(newMd: NodeMetadata): MapAlgebraAST =
      copy(metadata = Some(newMd))
  }

  sealed trait UnaryOperation extends Operation with Serializable

  final case class Masking(args: List[MapAlgebraAST],
                           id: UUID,
                           metadata: Option[NodeMetadata],
                           mask: MultiPolygon)
      extends UnaryOperation {
    val symbol = "mask"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST =
      copy(args = newArgs)
    def withMetadata(newMd: NodeMetadata): MapAlgebraAST =
      copy(metadata = Some(newMd))
  }

  final case class Classification(args: List[MapAlgebraAST],
                                  id: UUID,
                                  metadata: Option[NodeMetadata],
                                  classMap: ClassMap)
      extends UnaryOperation {
    val symbol = "classify"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST =
      copy(args = newArgs)
    def withMetadata(newMd: NodeMetadata): MapAlgebraAST =
      copy(metadata = Some(newMd))
  }

  final case class IsDefined(args: List[MapAlgebraAST],
                             id: UUID,
                             metadata: Option[NodeMetadata])
      extends UnaryOperation {
    val symbol = "isdefined"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST =
      copy(args = newArgs)
    def withMetadata(newMd: NodeMetadata): MapAlgebraAST =
      copy(metadata = Some(newMd))
  }

  final case class IsUndefined(args: List[MapAlgebraAST],
                               id: UUID,
                               metadata: Option[NodeMetadata])
      extends UnaryOperation {
    val symbol = "isundefined"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST =
      copy(args = newArgs)
    def withMetadata(newMd: NodeMetadata): MapAlgebraAST =
      copy(metadata = Some(newMd))
  }

  final case class SquareRoot(args: List[MapAlgebraAST],
                              id: UUID,
                              metadata: Option[NodeMetadata])
      extends UnaryOperation {
    val symbol = "sqrt"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST =
      copy(args = newArgs)
    def withMetadata(newMd: NodeMetadata): MapAlgebraAST =
      copy(metadata = Some(newMd))
  }

  final case class Log(args: List[MapAlgebraAST],
                       id: UUID,
                       metadata: Option[NodeMetadata])
      extends UnaryOperation {
    val symbol = "log"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST =
      copy(args = newArgs)
    def withMetadata(newMd: NodeMetadata): MapAlgebraAST =
      copy(metadata = Some(newMd))
  }

  final case class Log10(args: List[MapAlgebraAST],
                         id: UUID,
                         metadata: Option[NodeMetadata])
      extends UnaryOperation {
    val symbol = "log10"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST =
      copy(args = newArgs)
    def withMetadata(newMd: NodeMetadata): MapAlgebraAST =
      copy(metadata = Some(newMd))
  }

  final case class Round(args: List[MapAlgebraAST],
                         id: UUID,
                         metadata: Option[NodeMetadata])
      extends UnaryOperation {
    val symbol = "round"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST =
      copy(args = newArgs)
    def withMetadata(newMd: NodeMetadata): MapAlgebraAST =
      copy(metadata = Some(newMd))
  }

  final case class Floor(args: List[MapAlgebraAST],
                         id: UUID,
                         metadata: Option[NodeMetadata])
      extends UnaryOperation {
    val symbol = "floor"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST =
      copy(args = newArgs)
    def withMetadata(newMd: NodeMetadata): MapAlgebraAST =
      copy(metadata = Some(newMd))
  }

  final case class Ceil(args: List[MapAlgebraAST],
                        id: UUID,
                        metadata: Option[NodeMetadata])
      extends UnaryOperation {
    val symbol = "ceil"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST =
      copy(args = newArgs)
    def withMetadata(newMd: NodeMetadata): MapAlgebraAST =
      copy(metadata = Some(newMd))
  }

  final case class NumericNegation(args: List[MapAlgebraAST],
                                   id: UUID,
                                   metadata: Option[NodeMetadata])
      extends UnaryOperation {
    val symbol = "neg"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST =
      copy(args = newArgs)
    def withMetadata(newMd: NodeMetadata): MapAlgebraAST =
      copy(metadata = Some(newMd))
  }

  final case class LogicalNegation(args: List[MapAlgebraAST],
                                   id: UUID,
                                   metadata: Option[NodeMetadata])
      extends UnaryOperation {
    val symbol = "not"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST =
      copy(args = newArgs)
    def withMetadata(newMd: NodeMetadata): MapAlgebraAST =
      copy(metadata = Some(newMd))
  }

  final case class Abs(args: List[MapAlgebraAST],
                       id: UUID,
                       metadata: Option[NodeMetadata])
      extends UnaryOperation {
    val symbol = "abs"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST =
      copy(args = newArgs)
    def withMetadata(newMd: NodeMetadata): MapAlgebraAST =
      copy(metadata = Some(newMd))
  }

  final case class Sin(args: List[MapAlgebraAST],
                       id: UUID,
                       metadata: Option[NodeMetadata])
      extends UnaryOperation {
    val symbol = "sin"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST =
      copy(args = newArgs)
    def withMetadata(newMd: NodeMetadata): MapAlgebraAST =
      copy(metadata = Some(newMd))
  }

  final case class Cos(args: List[MapAlgebraAST],
                       id: UUID,
                       metadata: Option[NodeMetadata])
      extends UnaryOperation {
    val symbol = "cos"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST =
      copy(args = newArgs)
    def withMetadata(newMd: NodeMetadata): MapAlgebraAST =
      copy(metadata = Some(newMd))
  }

  final case class Tan(args: List[MapAlgebraAST],
                       id: UUID,
                       metadata: Option[NodeMetadata])
      extends UnaryOperation {
    val symbol = "tan"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST =
      copy(args = newArgs)
    def withMetadata(newMd: NodeMetadata): MapAlgebraAST =
      copy(metadata = Some(newMd))
  }

  final case class Sinh(args: List[MapAlgebraAST],
                        id: UUID,
                        metadata: Option[NodeMetadata])
      extends UnaryOperation {
    val symbol = "sinh"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST =
      copy(args = newArgs)
    def withMetadata(newMd: NodeMetadata): MapAlgebraAST =
      copy(metadata = Some(newMd))
  }

  final case class Cosh(args: List[MapAlgebraAST],
                        id: UUID,
                        metadata: Option[NodeMetadata])
      extends UnaryOperation {
    val symbol = "cosh"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST =
      copy(args = newArgs)
    def withMetadata(newMd: NodeMetadata): MapAlgebraAST =
      copy(metadata = Some(newMd))
  }

  final case class Tanh(args: List[MapAlgebraAST],
                        id: UUID,
                        metadata: Option[NodeMetadata])
      extends UnaryOperation {
    val symbol = "tanh"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST =
      copy(args = newArgs)
    def withMetadata(newMd: NodeMetadata): MapAlgebraAST =
      copy(metadata = Some(newMd))
  }

  final case class Asin(args: List[MapAlgebraAST],
                        id: UUID,
                        metadata: Option[NodeMetadata])
      extends UnaryOperation {
    val symbol = "asin"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST =
      copy(args = newArgs)
    def withMetadata(newMd: NodeMetadata): MapAlgebraAST =
      copy(metadata = Some(newMd))
  }

  final case class Acos(args: List[MapAlgebraAST],
                        id: UUID,
                        metadata: Option[NodeMetadata])
      extends UnaryOperation {
    val symbol = "acos"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST =
      copy(args = newArgs)
    def withMetadata(newMd: NodeMetadata): MapAlgebraAST =
      copy(metadata = Some(newMd))
  }

  final case class Atan(args: List[MapAlgebraAST],
                        id: UUID,
                        metadata: Option[NodeMetadata])
      extends UnaryOperation {
    val symbol = "atan"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST =
      copy(args = newArgs)
    def withMetadata(newMd: NodeMetadata): MapAlgebraAST =
      copy(metadata = Some(newMd))
  }

  sealed trait FocalOperation extends UnaryOperation {
    def neighborhood: Neighborhood
  }

  final case class FocalMax(args: List[MapAlgebraAST],
                            id: UUID,
                            metadata: Option[NodeMetadata],
                            neighborhood: Neighborhood)
      extends FocalOperation {
    val symbol = "focalMax"
    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST =
      copy(args = newArgs)
    def withMetadata(newMd: NodeMetadata): MapAlgebraAST =
      copy(metadata = Some(newMd))
  }

  final case class FocalMin(args: List[MapAlgebraAST],
                            id: UUID,
                            metadata: Option[NodeMetadata],
                            neighborhood: Neighborhood)
      extends FocalOperation {
    val symbol = "focalMin"
    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST =
      copy(args = newArgs)
    def withMetadata(newMd: NodeMetadata): MapAlgebraAST =
      copy(metadata = Some(newMd))
  }

  final case class FocalMean(args: List[MapAlgebraAST],
                             id: UUID,
                             metadata: Option[NodeMetadata],
                             neighborhood: Neighborhood)
      extends FocalOperation {
    val symbol = "focalMean"
    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST =
      copy(args = newArgs)
    def withMetadata(newMd: NodeMetadata): MapAlgebraAST =
      copy(metadata = Some(newMd))
  }

  final case class FocalMedian(args: List[MapAlgebraAST],
                               id: UUID,
                               metadata: Option[NodeMetadata],
                               neighborhood: Neighborhood)
      extends FocalOperation {
    val symbol = "focalMedian"
    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST =
      copy(args = newArgs)
    def withMetadata(newMd: NodeMetadata): MapAlgebraAST =
      copy(metadata = Some(newMd))
  }

  final case class FocalMode(args: List[MapAlgebraAST],
                             id: UUID,
                             metadata: Option[NodeMetadata],
                             neighborhood: Neighborhood)
      extends FocalOperation {
    val symbol = "focalMode"
    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST =
      copy(args = newArgs)
    def withMetadata(newMd: NodeMetadata): MapAlgebraAST =
      copy(metadata = Some(newMd))
  }

  final case class FocalSum(args: List[MapAlgebraAST],
                            id: UUID,
                            metadata: Option[NodeMetadata],
                            neighborhood: Neighborhood)
      extends FocalOperation {
    val symbol = "focalSum"
    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST =
      copy(args = newArgs)
    def withMetadata(newMd: NodeMetadata): MapAlgebraAST =
      copy(metadata = Some(newMd))
  }

  final case class FocalStdDev(args: List[MapAlgebraAST],
                               id: UUID,
                               metadata: Option[NodeMetadata],
                               neighborhood: Neighborhood)
      extends FocalOperation {
    val symbol = "focalStdDev"
    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST =
      copy(args = newArgs)
    def withMetadata(newMd: NodeMetadata): MapAlgebraAST =
      copy(metadata = Some(newMd))
  }

  sealed trait MapAlgebraLeaf extends MapAlgebraAST {
    val `type`: String
    def args: List[MapAlgebraAST] = List.empty
    def find(id: UUID): Option[MapAlgebraAST] =
      if (this.id == id) Some(this)
      else None
    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = this
  }

  final case class Constant(id: UUID,
                            constant: Double,
                            metadata: Option[NodeMetadata])
      extends MapAlgebraLeaf {
    val `type` = "const"
    def sources: Seq[MapAlgebraAST.MapAlgebraLeaf] = List()
    def substitute(
        substitutions: Map[UUID, MapAlgebraAST]): Option[MapAlgebraAST] =
      Some(this)
    def withMetadata(newMd: NodeMetadata): MapAlgebraAST =
      copy(metadata = Some(newMd))
  }

  /** Map Algebra sources */
  final case class LiteralTile(id: UUID,
                               lt: Tile,
                               metadata: Option[NodeMetadata])
      extends MapAlgebraLeaf {
    val `type` = "rasterLiteral"
    def sources: Seq[MapAlgebraAST.MapAlgebraLeaf] = List(this)
    def substitute(
        substitutions: Map[UUID, MapAlgebraAST]): Option[MapAlgebraAST] =
      Some(this)
    def withMetadata(newMd: NodeMetadata): MapAlgebraAST =
      copy(metadata = Some(newMd))
  }

  final case class ProjectRaster(id: UUID,
                                 projId: UUID,
                                 band: Option[Int],
                                 celltype: Option[CellType],
                                 metadata: Option[NodeMetadata])
      extends MapAlgebraLeaf
      with RFMLRaster {
    val `type` = "projectSrc"
    def sources: Seq[MapAlgebraAST.MapAlgebraLeaf] = List(this)
    def substitute(
        substitutions: Map[UUID, MapAlgebraAST]): Option[MapAlgebraAST] =
      Some(this)
    def withMetadata(newMd: NodeMetadata): MapAlgebraAST =
      copy(metadata = Some(newMd))

  }

  final case class LayerRaster(id: UUID,
                               layerId: UUID,
                               band: Option[Int],
                               celltype: Option[CellType],
                               metadata: Option[NodeMetadata])
      extends MapAlgebraLeaf
      with RFMLRaster {
    val `type` = "layerSrc"
    def sources: Seq[MapAlgebraAST.MapAlgebraLeaf] = List(this)
    def substitute(
        substitutions: Map[UUID, MapAlgebraAST]): Option[MapAlgebraAST] =
      Some(this)
    def withMetadata(newMd: NodeMetadata): MapAlgebraAST =
      copy(metadata = Some(newMd))
  }

  final case class ToolReference(id: UUID, toolId: UUID)
      extends MapAlgebraLeaf {
    val `type` = "ref"

    def metadata: Option[NodeMetadata] = None
    def sources: List[MapAlgebraAST.MapAlgebraLeaf] = Nil
    def substitute(
        substitutions: Map[UUID, MapAlgebraAST]): Option[MapAlgebraAST] =
      substitutions.get(toolId)
    def withMetadata(newMd: NodeMetadata): MapAlgebraAST = this
  }

}
