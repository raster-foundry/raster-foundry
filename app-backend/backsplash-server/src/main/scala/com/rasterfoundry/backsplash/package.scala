package com.rasterfoundry.backsplash

import geotrellis.raster.summary._
import io.circe.generic.semiauto._
import io.circe.{Encoder, KeyEncoder}

package object server {

  // Without this keyencoder we can't encode the bincounts from double histograms
  implicit val encodeKeyDouble: KeyEncoder[Double] = new KeyEncoder[Double] {
    def apply(key: Double): String = key.toString
  }

  implicit val statsEncoder: Encoder[Statistics[Double]] = deriveEncoder
}
