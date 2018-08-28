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

  implicit class CirceMapAlgebraJsonMethods(val self: Json) {
    def _id: Option[UUID] =
      root.id.string.getOption(self).map(UUID.fromString(_))
    def _type: Option[String] = root.`type`.string.getOption(self)
    def _label: Option[String] = root.metadata.label.string.getOption(self)
    def _symbol: Option[String] =
      root.selectDynamic("apply").string.getOption(self)

    def _keys: Seq[String] =
      root.obj.getOption(self).map(_.keys.toSeq).getOrElse(Seq())
  }

  implicit class CirceMapAlgebraHCursorMethods(val self: HCursor) {
    def _id: Option[UUID] = self.value._id
    def _type: Option[String] = self.value._type
    def _label: Option[String] = self.value._label
    def _symbol: Option[String] = self.value._symbol

    def _keys: Seq[String] = self.value._keys
  }

  implicit class MapAlgebraASTHelperMethods(val self: MapAlgebraAST) {
    private def generateMetadata =
      Some(
        NodeMetadata(
          Some(s"${self.metadata.flatMap(_.label).getOrElse(self.id)}"),
          None,
          None
        ))

    def classify(classmap: ClassMap) =
      MapAlgebraAST.Classification(List(self),
                                   UUID.randomUUID(),
                                   generateMetadata,
                                   classmap)

    def +(other: MapAlgebraAST): MapAlgebraAST.Operation =
      MapAlgebraAST.Addition(List(self, other),
                             UUID.randomUUID(),
                             generateMetadata)

    def -(other: MapAlgebraAST): MapAlgebraAST.Operation =
      MapAlgebraAST.Subtraction(List(self, other),
                                UUID.randomUUID(),
                                generateMetadata)

    def *(other: MapAlgebraAST): MapAlgebraAST.Operation =
      MapAlgebraAST.Multiplication(List(self, other),
                                   UUID.randomUUID(),
                                   generateMetadata)

    def /(other: MapAlgebraAST): MapAlgebraAST.Operation =
      MapAlgebraAST.Division(List(self, other),
                             UUID.randomUUID(),
                             generateMetadata)

    def max(other: MapAlgebraAST): MapAlgebraAST.Operation =
      MapAlgebraAST.Max(List(self, other), UUID.randomUUID(), generateMetadata)

    def min(other: MapAlgebraAST): MapAlgebraAST.Operation =
      MapAlgebraAST.Min(List(self, other), UUID.randomUUID(), generateMetadata)
  }
}
