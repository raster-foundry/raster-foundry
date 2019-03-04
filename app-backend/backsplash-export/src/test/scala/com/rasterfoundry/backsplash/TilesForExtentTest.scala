package com.rasterfoundry.backsplash.export

import geotrellis.vector._
import org.scalatest._
import org.scalatest.prop.Checkers

class TilesForExtentSpec extends FunSuite with Checkers with Matchers {
  test("Should produce a list of all tiles under some LatLng extent") {
    val extent = Extent(-103.095703125,
                        39.198205348894795,
                        -93.603515625,
                        42.22851735620852)

    val tileAddresses = TilesForExtent.latLng(extent, 5)
    assert(tileAddresses.contains((1, 2)))
    assert(tileAddresses.contains((1, 3)))
  }
}