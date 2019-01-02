package com.rasterfoundry

import cats.effect._
import io.circe.KeyEncoder

package object backsplash {

  type BacksplashMosaic = fs2.Stream[IO, BacksplashImage]

  implicit val encodeKeyDouble: KeyEncoder[Double] = new KeyEncoder[Double] {
    final def apply(key: Double): String = key.toString
  }
}
