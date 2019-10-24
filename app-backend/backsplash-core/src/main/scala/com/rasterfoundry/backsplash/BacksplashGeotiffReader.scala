package com.rasterfoundry.backsplash

import java.nio.ByteOrder

import com.rasterfoundry.common.BacksplashGeoTiffInfo
import geotrellis.raster.io.geotiff._
import geotrellis.raster.io.geotiff.reader.GeoTiffReader.GeoTiffInfo
import geotrellis.raster.io.geotiff.reader.{
  GeoTiffReader,
  MalformedGeoTiffException,
  TiffTagsReader
}
import geotrellis.raster.io.geotiff.tags.TiffTags
import geotrellis.raster.io.geotiff.util._
import geotrellis.util.ByteReader
import cats.data.{NonEmptyList => NEL}
import com.typesafe.scalalogging.LazyLogging

import scala.collection.mutable.ListBuffer

object BacksplashGeotiffReader extends LazyLogging {

  import GeoTiffReader.geoTiffMultibandTile

  /** Creates Lazy/Streaming MultibandGeoTiff from GeoTiffInfo
    *
    * useful to avoid overhead of reading metadata
    *
    * @param providedInfo
    * @return
    */
  def readMultibandWithInfo(providedInfo: GeoTiffInfo): MultibandGeoTiff = {
    def getMultiband(geoTiffTile: GeoTiffMultibandTile,
                     info: GeoTiffInfo): MultibandGeoTiff = {

      new MultibandGeoTiff(
        geoTiffTile,
        info.extent,
        info.crs,
        info.tags,
        info.options,
        info.overviews.map { i =>
          getMultiband(geoTiffMultibandTile(i), i)
        }
      )
    }

    val geoTiffTile = geoTiffMultibandTile(providedInfo)

    getMultiband(geoTiffTile, providedInfo)

  }

  /** This method is copy/pasted from  GeoTrellis source code
    * because we need tiff tags for the native resolution
    * and any overviews in order to recreate [[GeoTiffInfo]] that
    * the reader needs
    *
    * @param byteReader
    * @param withOverviews
    * @return
    */
  def getAllTiffTags(byteReader: ByteReader,
                     withOverviews: Boolean): List[TiffTags] = {
    val oldPos = byteReader.position
    try {
      byteReader.position(0)
      // set byte ordering
      (byteReader.get.toChar, byteReader.get.toChar) match {
        case ('I', 'I') =>
          byteReader.order(ByteOrder.LITTLE_ENDIAN)
        case ('M', 'M') =>
          byteReader.order(ByteOrder.BIG_ENDIAN)
        case _ => throw new MalformedGeoTiffException("incorrect byte order")
      }

      byteReader.position(oldPos + 2)
      // Validate Tiff identification number
      val tiffIdNumber = byteReader.getChar
      if (tiffIdNumber != 42 && tiffIdNumber != 43)
        throw new MalformedGeoTiffException(
          s"bad identification number (must be 42 or 43, was $tiffIdNumber (${tiffIdNumber.toInt}))"
        )

      val tiffType = TiffType.fromCode(tiffIdNumber)

      val baseTiffTags: TiffTags =
        tiffType match {
          case Tiff =>
            val smallStart = byteReader.getInt
            TiffTagsReader.read(byteReader, smallStart.toLong)(
              IntTiffTagOffsetSize
            )
          case _ =>
            byteReader.position(8)
            val bigStart = byteReader.getLong
            TiffTagsReader.read(byteReader, bigStart)(LongTiffTagOffsetSize)
        }

      // IFD overviews may contain not all tags required for a proper work with it
      // for instance it may not contain CRS metadata
      val tiffTagsList: List[TiffTags] = {
        val tiffTagsBuffer: ListBuffer[TiffTags] = ListBuffer()
        if (withOverviews) {
          tiffType match {
            case Tiff =>
              var ifdOffset = byteReader.getInt
              while (ifdOffset > 0) {
                tiffTagsBuffer += TiffTagsReader.read(byteReader, ifdOffset)(
                  IntTiffTagOffsetSize
                )
                ifdOffset = byteReader.getInt
              }
            case _ =>
              var ifdOffset = byteReader.getLong
              while (ifdOffset > 0) {
                tiffTagsBuffer += TiffTagsReader.read(byteReader, ifdOffset)(
                  LongTiffTagOffsetSize
                )
                ifdOffset = byteReader.getLong
              }
          }
        }
        tiffTagsBuffer.toList
      }
      baseTiffTags :: tiffTagsList
    } finally {
      byteReader.position(oldPos)
      ()
    }
  }

  /** Helper function that gets a serializable [[BacksplashGeoTiffInfo]] given
    * a URI
    *
    * @param uri
    * @return
    */
  def getBacksplashGeotiffInfo(uri: String): BacksplashGeoTiffInfo = {
    val reader = getByteReader(uri)
    val geoTiffInfo = GeoTiffReader.readGeoTiffInfo(reader, true, true)
    logger.debug(
      s"Some Geotiff Info for $uri: COMPRESSION: ${geoTiffInfo.compression} SEGMENT LAYOUT: ${geoTiffInfo.segmentLayout}")
    val tiffTags = NEL.fromListUnsafe(getAllTiffTags(reader, true))
    BacksplashGeoTiffInfo.fromGeotiffInfo(geoTiffInfo, tiffTags)
  }
}
