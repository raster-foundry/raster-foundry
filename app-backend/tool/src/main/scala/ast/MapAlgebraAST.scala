package com.azavea.rf.tool.ast

import java.util.UUID

import io.circe.generic.JsonCodec


/** The ur-type for a recursive representation of MapAlgebra operations */
sealed trait MapAlgebraAST extends Product with Serializable {
  def id: UUID
  def args: List[MapAlgebraAST]
  def label: Option[String]
  def find(id: UUID): Option[MapAlgebraAST]
  def sources: Seq[UUID]
  def validateSources(sourceIds: Seq[UUID]): Boolean =
    sources.foldLeft(true)(_ && sourceIds.contains(_))

}

object MapAlgebraAST {
  /** Map Algebra operations (nodes in this tree) */
  abstract class Operation(val symbol: String) extends MapAlgebraAST {
    def find(id: UUID): Option[MapAlgebraAST] =
      if (this.id == id)
        Some(this)
      else {
        val matches = this.args.flatMap(_.find(id))
        require(matches.length < 2, s"Ambiguous IDs ($matches) on Map Algebra AST ($this)")
        matches.headOption
      }
    def sources: Seq[UUID] = this.args.flatMap(_.sources)
  }

  @JsonCodec
  case class Addition(args: List[MapAlgebraAST], id: UUID, label: Option[String])
      extends Operation("+")

  @JsonCodec
  case class Subtraction(args: List[MapAlgebraAST], id: UUID, label: Option[String])
      extends Operation("-")

  @JsonCodec
  case class Multiplication(args: List[MapAlgebraAST], id: UUID, label: Option[String])
      extends Operation("*")

  @JsonCodec
  case class Division(args: List[MapAlgebraAST], id: UUID, label: Option[String])
      extends Operation("/")

  @JsonCodec
  case class Masking(args: List[MapAlgebraAST], id: UUID, label: Option[String])
      extends Operation("mask")

  @JsonCodec
  case class Classification(args: List[MapAlgebraAST], id: UUID, label: Option[String], classBreaks: ClassBreaks)
      extends Operation("classify")

  /** Map Algebra sources (leaves) */
  @JsonCodec
  case class Source(id: UUID, label: Option[String]) extends MapAlgebraAST {
    def args: List[MapAlgebraAST] = List.empty

    def find(id: UUID): Option[MapAlgebraAST] =
      if (this.id == id) Some(this)
      else None
    def sources: Seq[UUID] = List(this.id)
  }

  object Source {
    def empty: Source = Source(UUID.randomUUID(), None)
  }

  /** TODO: Add other source types (or treat of them as hyperparameters - e.g. ClassBreaks, above) */

}
