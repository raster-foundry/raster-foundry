package com.azavea.rf

import geotrellis.raster.MultibandTile
import geotrellis.raster.split._
import geotrellis.spark._
import geotrellis.spark.tiling._
import geotrellis.util.Component
import geotrellis.vector._

package object export {
  implicit class withRasterFoundryTilerKeyMethods(val self: (ProjectedExtent, Int))
      extends TilerKeyMethods[(ProjectedExtent, Int), (SpatialKey, Int)] {
    def extent = self._1.extent
    def translate(spatialKey: SpatialKey) = (spatialKey, self._2)
  }

  implicit val rfSpatialKeyIntComponent =
    Component[(SpatialKey, Int), SpatialKey](from => from._1, (from, to) => (to, from._2))

  implicit val rfProjectedExtentIntComponent =
    Component[(ProjectedExtent, Int), ProjectedExtent](from => from._1, (from, to) => (to, from._2))

  implicit class withMultibandTileSplitMethods(val self: MultibandTile) extends MultibandTileSplitMethods
}
