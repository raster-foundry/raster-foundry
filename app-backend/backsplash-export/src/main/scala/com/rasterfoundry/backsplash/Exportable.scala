package com.rasterfoundry.backsplash.export

import geotrellis.proj4.{CRS, LatLng, WebMercator}
import geotrellis.vector.Extent
import geotrellis.raster.io.geotiff._
import geotrellis.raster.io.geotiff.compression._
import geotrellis.raster._
import simulacrum._
import cats.effect._
import java.util.UUID

/**
  * Allows arbitrary types to encode exports by writing the appropriate instances
  */
@typeclass trait Exportable[A] {
  @op("keyedTileSegments") def keyedTileSegments(self: A, zoom: Int)(
      implicit cs: ContextShift[IO]): Iterator[((Int, Int), MultibandTile)]

  @op("exportCellType") def exportCellType(self: A): CellType

  @op("exportExtent") def exportExtent(self: A): Extent

  // I imagine we'll just be using webmercator for now
  @op("exportCRS") def exportCRS(self: A): CRS = WebMercator

  @op("exportZoom") def exportZoom(self: A): Int

  @op("segmentLayout") def segmentLayout(self: A): GeoTiffSegmentLayout

  @op("exportDestination") def exportDestination(self: A): String

  @op("toGeoTiff") def toGeoTiff(self: A, compression: Compression)(
      implicit cs: ContextShift[IO]): MultibandGeoTiff = {
    val tifftile = GeoTiffBuilder[MultibandTile]
      .makeTile(
        keyedTileSegments(self, exportZoom(self)),
        segmentLayout = segmentLayout(self),
        cellType = exportCellType(self),
        compression = compression
      )
      .asInstanceOf[GeoTiffMultibandTile] // This hurts :(
    val latLngExtent = exportExtent(self)
    val tilesForExtent = TilesForExtent.latLng(latLngExtent, exportZoom(self))
    val outputExtent =
      ExtentOfTiles.webMercator(tilesForExtent, exportZoom(self))
    MultibandGeoTiff(tifftile, outputExtent, exportCRS(self))
  }
}
