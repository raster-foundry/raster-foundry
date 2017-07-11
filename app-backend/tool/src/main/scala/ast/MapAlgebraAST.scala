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
  sealed trait UnaryOperation extends Operation with Serializable

  sealed trait FocalOperation extends UnaryOperation {
    val neighborhood: Neighborhood
  }

  case class Addition(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata]) extends Operation {
  val symbol = "+"

  def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = Addition(newArgs, id, metadata)
}

  case class Subtraction(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata]) extends Operation {
    val symbol = "-"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = Subtraction(newArgs, id, metadata)
  }

  case class Multiplication(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata]) extends Operation {
    val symbol = "*"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = Multiplication(newArgs, id, metadata)
  }

  case class Division(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata]) extends Operation {
    val symbol = "/"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = Division(newArgs, id, metadata)
  }

  case class Masking(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata], mask: MultiPolygon) extends UnaryOperation {
    val symbol = "mask"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = Masking(newArgs, id, metadata, mask)
  }

  case class Classification(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata], classMap: ClassMap) extends UnaryOperation {
    val symbol = "classify"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = Classification(newArgs, id, metadata, classMap)
  }

  case class Max(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata]) extends Operation {
    val symbol = "max"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = Max(newArgs, id, metadata)
  }

  case class Min(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata]) extends Operation {
    val symbol = "min"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = Min(newArgs, id, metadata)
  }

  case class FocalMax(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata], neighborhood: Neighborhood) extends FocalOperation {
    val symbol = "focalMax"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = FocalMax(newArgs, id, metadata, neighborhood)
  }

  case class FocalMin(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata], neighborhood: Neighborhood) extends FocalOperation {
    val symbol = "focalMin"

    def withArgs(newArgs: List[MapAlgebraAST]): MapAlgebraAST = FocalMin(newArgs, id, metadata, neighborhood)
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

  object Source {
    def empty: Source = Source(UUID.randomUUID(), None)
  }

  case class ToolReference(id: UUID, toolId: UUID) extends MapAlgebraLeaf {
    val `type` = "ref"

    def metadata: Option[NodeMetadata] = None
    def sources: List[MapAlgebraAST.Source] = List()
    def substitute(substitutions: Map[UUID, MapAlgebraAST]): Option[MapAlgebraAST] =
      substitutions.get(toolId)
  }

}
