package com.rasterfoundry.backsplash.export

import cats.effect._
import geotrellis.proj4.{CRS, WebMercator}
import geotrellis.raster._
import geotrellis.raster.io.geotiff._
import geotrellis.raster.io.geotiff.compression._
import geotrellis.vector.Extent
import simulacrum._

/**
  * Allows arbitrary types to encode exports by writing the appropriate instances
  */
@typeclass trait Exportable[A] {
  @op("keyedTileSegments") def keyedTileSegments(self: A, zoom: Int)(
      implicit cs: ContextShift[IO]): Iterator[((Int, Int), MultibandTile)]

  @op("exportCellType") def exportCellType(self: A): CellType

  @op("exportExtent") def exportExtent(self: A): Extent

  // I imagine we'll just be using webmercator for now
  @op("exportCRS") def exportCRS: CRS = WebMercator

  @op("exportZoom") def exportZoom(self: A): Int

  @op("segmentLayout") def segmentLayout(self: A): GeoTiffSegmentLayout

  @op("exportDestination") def exportDestination(self: A): String

  @op("toGeoTiff")
  def toGeoTiff(self: A, compression: Compression)(
      implicit cs: ContextShift[IO]): MultibandGeoTiff = {

    /** It's fine, maybe */
    @SuppressWarnings(Array("AsInstanceOf"))
    def tifftile: GeoTiffMultibandTile =
      GeoTiffBuilder[MultibandTile]
        .makeTile(
          keyedTileSegments(self, exportZoom(self)),
          segmentLayout = segmentLayout(self),
          cellType = exportCellType(self),
          compression = compression
        )
        .asInstanceOf[GeoTiffMultibandTile]
    val latLngExtent = exportExtent(self)
    val tilesForExtent = TilesForExtent.latLng(latLngExtent, exportZoom(self))
    val outputExtent =
      ExtentOfTiles.webMercator(tilesForExtent, exportZoom(self))
    MultibandGeoTiff(tifftile, outputExtent, exportCRS)
  }
}
