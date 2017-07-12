package com.azavea.rf.tool

import com.azavea.rf.tool.ast._
import com.azavea.rf.tool.ast.MapAlgebraAST._
import com.azavea.rf.tool.ast.codec.MapAlgebraCodec

import io.circe._
import io.circe.optics.JsonPath._
import cats._
import cats.data._
import cats.implicits._

import scala.concurrent._
import java.util.UUID

package object ast extends MapAlgebraCodec {

  /** Collect resources necessary to carry out substitutions on  ASTs while avoiding cycles */
  def assembleSubstitutions(
    ast: MapAlgebraAST,
    substitutionResolver: UUID => Future[Option[MapAlgebraAST]],
    assembled: Map[UUID, MapAlgebraAST] = Map()
  )(implicit ec: ExecutionContext): OptionT[Future, Map[UUID, MapAlgebraAST]] =
    ast match {
      case ToolReference(id, refId) =>
        OptionT(substitutionResolver(refId)).flatMap({ astPatch =>
          // Here's where we can hope to catch cycles as we traverse down
          //  the tree (keeping track of previously encountered references)
          if (assembled.get(refId).isDefined)
            // TODO: EitherT this thing
            throw new IllegalArgumentException(s"Repeated substitution $refId found; likely cyclical reference")
          else
            assembleSubstitutions(astPatch, substitutionResolver, assembled ++ Map(refId -> astPatch))
        })
      case op: MapAlgebraAST.Operation =>
        val childSubstitutions = op.args.map({ arg => assembleSubstitutions(arg, substitutionResolver, assembled) }).sequence
        childSubstitutions.map({ substitutions =>
          substitutions.foldLeft(Map[UUID, MapAlgebraAST]())({ case (map, subs) => map ++ subs }) ++ assembled
        })
      case _ =>
        OptionT.pure[Future, Map[UUID, MapAlgebraAST]](assembled)
      }

  implicit class CirceMapAlgebraJsonMethods(val self: Json) {
    def _id: Option[UUID] = root.id.string.getOption(self).map(UUID.fromString(_))
    def _type: Option[String] = root.`type`.string.getOption(self)
    def _label: Option[String] = root.metadata.label.string.getOption(self)
    def _symbol: Option[String] = root.selectDynamic("apply").string.getOption(self)

    def _fields: Seq[String] = root.obj.getOption(self).map(_.fields).getOrElse(Seq())
  }

  implicit class CirceMapAlgebraHCursorMethods(val self: HCursor) {
    def _id: Option[UUID] = self.value._id
    def _type: Option[String] = self.value._type
    def _label: Option[String] = self.value._label
    def _symbol: Option[String] = self.value._symbol

    def _fields: Seq[String] = self.value._fields
  }

  implicit class MapAlgebraASTHelperMethods(val self: MapAlgebraAST) {
    private def generateMetadata = Some(NodeMetadata(
      Some(s"${self.metadata.flatMap(_.label).getOrElse(self.id)}"),
      None,
      None
    ))

    def classify(classmap: ClassMap) =
      MapAlgebraAST.Classification(List(self), UUID.randomUUID(), generateMetadata, classmap)

    def +(other: MapAlgebraAST): MapAlgebraAST.Operation =
      MapAlgebraAST.Addition(List(self, other), UUID.randomUUID(), generateMetadata)

    def -(other: MapAlgebraAST): MapAlgebraAST.Operation =
      MapAlgebraAST.Subtraction(List(self, other), UUID.randomUUID(), generateMetadata)

    def *(other: MapAlgebraAST): MapAlgebraAST.Operation =
      MapAlgebraAST.Multiplication(List(self, other), UUID.randomUUID(), generateMetadata)

    def /(other: MapAlgebraAST): MapAlgebraAST.Operation =
      MapAlgebraAST.Division(List(self, other), UUID.randomUUID(), generateMetadata)

    def max(other: MapAlgebraAST): MapAlgebraAST.Operation =
      MapAlgebraAST.Max(List(self, other), UUID.randomUUID(), generateMetadata)

    def min(other: MapAlgebraAST): MapAlgebraAST.Operation =
      MapAlgebraAST.Min(List(self, other), UUID.randomUUID(), generateMetadata)
  }
}
