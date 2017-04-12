package com.azavea.rf.common.cache.circe

import io.circe.{Decoder, ObjectEncoder}
import io.circe.export.Exported
import io.circe.generic.AutoDerivation
import io.circe.generic.decoding.DerivedDecoder
import io.circe.generic.encoding.DerivedObjectEncoder
import io.circe.generic.util.macros.ExportMacros

import scala.language.experimental.macros
import shapeless.Cached

import scala.language.experimental.macros

object CachedAutoDerivation extends CachedAutoDerivation

trait CachedAutoDerivation {
  implicit def cachedExportDecoder[A]: Exported[Decoder[A]] = {
    def decoder: Exported[Decoder[A]] = macro ExportMacros.exportDecoder[DerivedDecoder, A]
    Cached.implicitly[Exported[Decoder[A]]]
  }
  implicit def cachedExportEncoder[A]: Exported[ObjectEncoder[A]] = {
    def encoder: Exported[ObjectEncoder[A]] = macro ExportMacros.exportEncoder[DerivedObjectEncoder, A]
    Cached.implicitly[Exported[ObjectEncoder[A]]]
  }
}
