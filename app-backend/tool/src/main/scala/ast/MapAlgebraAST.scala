package com.azavea.rf.tool.ast

import java.util.UUID

import io.circe.generic.JsonCodec


/** The ur-type for a recursive representation of MapAlgebra operations */
sealed trait MapAlgebraAST extends Product with Serializable {
  def id: UUID
  def args: List[MapAlgebraAST]
  def metadata: Option[NodeMetadata]
  def find(id: UUID): Option[MapAlgebraAST]
  def sources: Seq[MapAlgebraAST.Source]
}

object MapAlgebraAST {
  /** Map Algebra operations (nodes in this tree) */
  abstract class Operation(val symbol: String) extends MapAlgebraAST {

    @SuppressWarnings(Array("TraversableHead"))
    def find(id: UUID): Option[MapAlgebraAST] =
      if (this.id == id)
        Some(this)
      else {
        val matches = args.flatMap(_.find(id))
        matches.headOption
      }

    def sources: Seq[MapAlgebraAST.Source] = args.flatMap(_.sources).distinct
  }

  /** Operations which should only have one argument. */
  abstract class UnaryOp(override val symbol: String) extends Operation(symbol)

  case class Addition(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata])
      extends Operation("+")

  case class Subtraction(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata])
      extends Operation("-")

  case class Multiplication(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata])
      extends Operation("*")

  case class Division(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata])
      extends Operation("/")

  case class Masking(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata])
      extends Operation("mask")

  case class Classification(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata], classMap: ClassMap)
      extends UnaryOp("classify")

  case class Max(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata])
      extends Operation("max")

  case class Min(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata])
      extends Operation("min")

  /** Map Algebra sources (leaves) */
  @JsonCodec
  case class Source(id: UUID, metadata: Option[NodeMetadata]) extends MapAlgebraAST {
    def args: List[MapAlgebraAST] = List.empty

    def find(id: UUID): Option[MapAlgebraAST] =
      if (this.id == id) Some(this)
      else None
    def sources: Seq[MapAlgebraAST.Source] = List(this)
  }

  object Source {
    def empty: Source = Source(UUID.randomUUID(), None)
  }

  /** TODO: Add other source types (or treat of them as hyperparameters - e.g. ClassMap, above) */

}
