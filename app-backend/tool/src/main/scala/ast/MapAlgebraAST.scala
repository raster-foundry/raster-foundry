package com.azavea.rf.tool.ast

import java.util.UUID

import io.circe.generic.JsonCodec


/** The ur-type for a recursive representation of MapAlgebra operations */
sealed trait MapAlgebraAST extends Product with Serializable {
  def id: UUID
  def args: List[MapAlgebraAST]
  def metadata: Option[NodeMetadata]
  def find(id: UUID): Option[MapAlgebraAST]
  def sources: Seq[UUID]

  def overrideMetadata(otherMD: Option[NodeMetadata]): Option[NodeMetadata] =
    (metadata, otherMD) match {
      case (Some(defaults), Some(overrides)) =>
        Some(NodeMetadata(
          overrides.label.orElse(defaults.label),
          overrides.description.orElse(defaults.description),
          overrides.histogram.orElse(defaults.histogram)
        ))
      case (None, Some(_)) => otherMD
      case (Some(_), None) => metadata
      case _ => None
    }
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
        require(matches.length < 2, s"Ambiguous IDs ($matches) on Map Algebra AST ($this)")
        matches.headOption
      }

    def sources: Seq[UUID] = args.flatMap(_.sources)
  }

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
      extends Operation("classify")

  /** Map Algebra sources (leaves) */
  @JsonCodec
  case class Source(id: UUID, metadata: Option[NodeMetadata]) extends MapAlgebraAST {
    def args: List[MapAlgebraAST] = List.empty

    def find(id: UUID): Option[MapAlgebraAST] =
      if (this.id == id) Some(this)
      else None
    def sources: Seq[UUID] = List(this.id)
  }

  object Source {
    def empty: Source = Source(UUID.randomUUID(), None)
  }

  /** TODO: Add other source types (or treat of them as hyperparameters - e.g. ClassMap, above) */

}
