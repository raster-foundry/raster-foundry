package com.rasterfoundry.common

import io.circe._
import io.circe.optics.JsonPath._

import java.util.UUID

package object ast {

  implicit class CirceMapAlgebraJsonMethods(val self: Json) {
    def idOpt: Option[UUID] =
      root.id.string.getOption(self).map(UUID.fromString(_))
    def typeOpt: Option[String] = root.`type`.string.getOption(self)
    def labelOpt: Option[String] = root.metadata.label.string.getOption(self)
    def symbolOpt: Option[String] =
      root.selectDynamic("apply").string.getOption(self)

    def keysSeq: Seq[String] =
      root.obj.getOption(self).map(_.keys.toSeq).getOrElse(Seq.empty)
  }

  implicit class CirceMapAlgebraHCursorMethods(val self: HCursor) {
    def idOpt: Option[UUID] = self.value.idOpt
    def typeOpt: Option[String] = self.value.typeOpt
    def labelOpt: Option[String] = self.value.labelOpt
    def symbolOpt: Option[String] = self.value.symbolOpt

    def keysSeq: Seq[String] = self.value.keysSeq
  }

  implicit class MapAlgebraASTHelperMethods(val self: MapAlgebraAST) {
    private def generateMetadata =
      Some(
        NodeMetadata(
          Some(s"${self.metadata.flatMap(_.label).getOrElse(self.id)}"),
          None,
          None
        )
      )

    def classify(classmap: ClassMap) =
      MapAlgebraAST.Classification(
        List(self),
        UUID.randomUUID(),
        generateMetadata,
        classmap
      )

    def +(other: MapAlgebraAST): MapAlgebraAST.Operation =
      MapAlgebraAST.Addition(
        List(self, other),
        UUID.randomUUID(),
        generateMetadata
      )

    def -(other: MapAlgebraAST): MapAlgebraAST.Operation =
      MapAlgebraAST.Subtraction(
        List(self, other),
        UUID.randomUUID(),
        generateMetadata
      )

    def *(other: MapAlgebraAST): MapAlgebraAST.Operation =
      MapAlgebraAST.Multiplication(
        List(self, other),
        UUID.randomUUID(),
        generateMetadata
      )

    def /(other: MapAlgebraAST): MapAlgebraAST.Operation =
      MapAlgebraAST.Division(
        List(self, other),
        UUID.randomUUID(),
        generateMetadata
      )

    def max(other: MapAlgebraAST): MapAlgebraAST.Operation =
      MapAlgebraAST.Max(List(self, other), UUID.randomUUID(), generateMetadata)

    def min(other: MapAlgebraAST): MapAlgebraAST.Operation =
      MapAlgebraAST.Min(List(self, other), UUID.randomUUID(), generateMetadata)
  }
}
