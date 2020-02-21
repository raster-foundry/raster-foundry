package com.rasterfoundry.backsplash

import geotrellis.raster.histogram._
import geotrellis.raster.io._
import geotrellis.raster.summary._
import io.circe.generic.semiauto._
import io.circe.parser._
import io.circe.syntax._
import io.circe.{Encoder, Json, KeyEncoder}
import spray.json._

package object server {

  // Without this keyencoder we can't encode the bincounts from double histograms
  implicit val encodeKeyDouble: KeyEncoder[Double] = new KeyEncoder[Double] {
    def apply(key: Double): String = key.toString
  }

  // utility codec for any spray json value
  implicit val sprayJsonEncoder: Encoder[JsValue] = new Encoder[JsValue] {
    def apply(jsvalue: JsValue): Json =
      parse(jsvalue.compactPrint) match {
        case Right(success) => success
        case Left(fail)     => throw fail
      }
  }

  // use spray's encoder (above) to encode histograms
  implicit val histogramEncoder: Encoder[Histogram[Double]] =
    new Encoder[Histogram[Double]] {
      def apply(hist: Histogram[Double]): Json = hist.toJson.asJson
    }

  implicit val statsEncoder: Encoder[Statistics[Double]] = deriveEncoder
}
