package com.rasterfoundry.common

import cats.data.{NonEmptyList => NEL}
import geotrellis.proj4.CRS
import geotrellis.raster.io.geotiff._
import geotrellis.raster.io.geotiff.compression.{Compression, Decompressor}
import geotrellis.raster.io.geotiff.reader.GeoTiffInfo
import geotrellis.raster.io.geotiff.tags.TiffTags
import geotrellis.util.StreamingByteReader
import geotrellis.vector.Extent

import java.nio.ByteOrder

/** Container for metadata for Tiffs
  *
  * This tracks closely to GeoTiffInfo, but omits and adds a few things
  * to make it easier to serialize. As such, this class has a static helper method
  * to construct itself from an instance of GeoTiffInfo and also transform back
  * into one
  *
  * @param extent
  * @param crs
  * @param tags
  * @param tiffTags
  * @param options
  * @param bandType
  * @param segmentLayout
  * @param compression
  * @param bandCount
  * @param noDataValue
  * @param overviews
  */
case class BacksplashGeoTiffInfo(extent: Extent,
                                 crs: CRS,
                                 tags: Tags,
                                 tiffTags: TiffTags,
                                 options: GeoTiffOptions,
                                 bandType: BandType,
                                 byteOrder: ByteOrder,
                                 segmentLayout: GeoTiffSegmentLayout,
                                 compression: Compression,
                                 bandCount: Int,
                                 noDataValue: Option[Double],
                                 overviews: List[BacksplashGeoTiffInfo] = Nil) {

  def toGeotiffInfo(segmentReader: StreamingByteReader): GeoTiffInfo = {
    GeoTiffInfo(
      extent,
      crs,
      tags,
      options,
      bandType,
      LazySegmentBytes(segmentReader, tiffTags),
      Decompressor(tiffTags, byteOrder),
      segmentLayout,
      compression,
      bandCount,
      noDataValue,
      overviews.map { info =>
        GeoTiffInfo(
          info.extent,
          info.crs,
          info.tags,
          info.options,
          info.bandType,
          LazySegmentBytes(segmentReader, info.tiffTags),
          Decompressor(info.tiffTags, byteOrder),
          info.segmentLayout,
          info.compression,
          info.bandCount,
          info.noDataValue
        )
      }
    )
  }
}

object BacksplashGeoTiffInfo {

  def fromGeotiffInfo(info: GeoTiffInfo,
                      tiffTags: NEL[TiffTags]): BacksplashGeoTiffInfo = {
    val byteOrder: ByteOrder = info.decompressor.byteOrder
    BacksplashGeoTiffInfo(
      info.extent,
      info.crs,
      info.tags,
      tiffTags.head,
      info.options,
      info.bandType,
      byteOrder,
      info.segmentLayout,
      info.compression,
      info.bandCount,
      info.noDataValue,
      info.overviews
        .zip(tiffTags.tail)
        .map {
          case (i, t) =>
            BacksplashGeoTiffInfo(i.extent,
                                  i.crs,
                                  i.tags,
                                  t,
                                  i.options,
                                  i.bandType,
                                  byteOrder,
                                  i.segmentLayout,
                                  i.compression,
                                  i.bandCount,
                                  i.noDataValue)
        }
    )
  }
}
