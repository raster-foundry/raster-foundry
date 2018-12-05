package com.rasterfoundry

import cats.effect._

package object backsplash {

  type BacksplashMosaic = fs2.Stream[IO, BacksplashImage]

}
