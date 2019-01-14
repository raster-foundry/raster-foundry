package com.rasterfoundry.backsplash

import com.rasterfoundry.database.ToolRunDao
import com.rasterfoundry.database.util.RFTransactor

import cats.effect.IO
import doobie.implicits._
import geotrellis.raster.{io => _, Tile}
import geotrellis.raster.io._
import geotrellis.raster.histogram._
import geotrellis.raster.render._
import geotrellis.raster.render.png._
import geotrellis.raster.summary._
import io.circe.{Encoder, Json, KeyEncoder}
import io.circe.generic.semiauto._
import io.circe.parser._
import io.circe.syntax._

import spray.json._
import DefaultJsonProtocol._

import java.util.UUID

package object server {

  // Without this keyencoder we can't encode the bincounts from double histograms
  implicit val encodeKeyDouble: KeyEncoder[Double] = new KeyEncoder[Double] {
    final def apply(key: Double): String = key.toString
  }

  // utility codec for any spray json value
  implicit val sprayJsonEncoder: Encoder[JsValue] = new Encoder[JsValue] {
    final def apply(jsvalue: JsValue): Json =
      parse(jsvalue.compactPrint) match {
        case Right(success) => success
        case Left(fail)     => throw fail
      }
  }

  // use spray's encoder (above) to encode histograms
  implicit val histogramEncoder: Encoder[Histogram[Double]] =
    new Encoder[Histogram[Double]] {
      final def apply(hist: Histogram[Double]): Json = hist.toJson.asJson
    }

  implicit val statsEncoder: Encoder[Statistics[Double]] = deriveEncoder
}
