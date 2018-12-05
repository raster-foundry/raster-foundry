package com.rasterfoundry.backsplash

import cats.effect.IO
import org.scalatest._
import org.scalatest.prop.Checkers
import org.scalacheck.Prop.forAll

import BacksplashImageGen._


class BacksplashMosaicSpec extends FunSuite with Checkers with Matchers {
  test("remove unnecessary images from mosaic, but not ones we need") {
    check {
      forAll {
        (mosaic1: BacksplashImage, mosaic2: BacksplashImage) =>
          var count = 0
          def work = { count = count + 1 }

          val mosaicStream = fs2.Stream.emits(List(mosaic1, mosaic2)).repeat.take(50)
          val relevantStream = BacksplashMosaic.filterRelevant(mosaicStream)

          relevantStream.map({ _ => work }).compile.drain.unsafeRunSync

          if (mosaic1 == mosaic2) {
            count == 1
          } else {
            count == 2
          }
      }
    }
  }
}
