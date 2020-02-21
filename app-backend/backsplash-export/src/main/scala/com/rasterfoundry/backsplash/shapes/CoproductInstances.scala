package com.rasterfoundry.backsplash.export.shapes

import com.rasterfoundry.backsplash.export._

import cats.effect._
import geotrellis.raster._
import shapeless.{:+:, CNil, Coproduct, Inl, Inr}

/** Necessary for evaluating the coproduct of exportable instances (allowing any type
  *  which implements the appropriate methods to count as, in some sense, defining an
  *  import. Inspired by this implementation:
  *  https://github.com/circe/circe/blob/backport/0.9.3/modules/shapes/src/main/scala/io/circe/shapes/CoproductInstances.scala
  */
trait CoproductInstances {
  implicit final val exportableCNil: Exportable[CNil] = new Exportable[CNil] {
    def keyedTileSegments(
        self: CNil,
        zoom: Int
    )(implicit cs: ContextShift[IO]): Iterator[((Int, Int), MultibandTile)] =
      sys.error("Cannot export CNil")

    def exportZoom(self: CNil): Int =
      sys.error("Cannot export CNil")

    def exportCellType(self: CNil) =
      sys.error("Cannot export CNil")

    def exportExtent(self: CNil) =
      sys.error("Cannot export CNil")

    def exportDestination(self: CNil) =
      sys.error("Cannot export CNil")

    def segmentLayout(self: CNil) =
      sys.error("Cannot export CNil")
  }

  implicit final def exportableCCons[L, R <: Coproduct](
      implicit
      exportL: Exportable[L],
      exportR: Exportable[R]
  ): Exportable[L :+: R] = new Exportable[L :+: R] {
    def keyedTileSegments(
        self: L :+: R,
        zoom: Int
    )(implicit cs: ContextShift[IO]): Iterator[((Int, Int), MultibandTile)] =
      self match {
        case Inl(l) => exportL.keyedTileSegments(l, zoom)
        case Inr(r) => exportR.keyedTileSegments(r, zoom)
      }

    def exportZoom(self: L :+: R): Int = self match {
      case Inl(l) => exportL.exportZoom(l)
      case Inr(r) => exportR.exportZoom(r)
    }

    def exportCellType(self: L :+: R) = self match {
      case Inl(l) => exportL.exportCellType(l)
      case Inr(r) => exportR.exportCellType(r)
    }

    def exportExtent(self: L :+: R) = self match {
      case Inl(l) => exportL.exportExtent(l)
      case Inr(r) => exportR.exportExtent(r)
    }

    def exportDestination(self: L :+: R) = self match {
      case Inl(l) => exportL.exportDestination(l)
      case Inr(r) => exportR.exportDestination(r)
    }

    def segmentLayout(self: L :+: R) = self match {
      case Inl(l) => exportL.segmentLayout(l)
      case Inr(r) => exportR.segmentLayout(r)
    }
  }
}
