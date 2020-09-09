package com.rasterfoundry.common

import cats.data.{NonEmptyList => NEL}
import com.amazonaws.services.s3.AmazonS3URI
import com.typesafe.scalalogging.LazyLogging
import geotrellis.raster.io.geotiff._
import geotrellis.raster.io.geotiff.reader.{
  GeoTiffInfo,
  MalformedGeoTiffException
}
import geotrellis.raster.io.geotiff.tags.TiffTags
import geotrellis.raster.io.geotiff.util._
import geotrellis.store.s3.util.S3RangeReader
import geotrellis.util.ByteReader
import geotrellis.util.{
  ByteReader,
  FileRangeReader,
  HttpRangeReader,
  StreamingByteReader
}
import org.apache.http.client.utils.URLEncodedUtils
import software.amazon.awssdk.services.s3.S3Client

import scala.collection.mutable.ListBuffer

import java.net.{URI, URL}
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.nio.file.Paths

object BacksplashGeotiffReader extends LazyLogging {

  /** AWS' S3 client has an internal connection pool, in order to maximize throughput
    * we try to reuse it and only instantiate one
    *
    */
  private lazy val s3Client = S3Client.builder().build()

  /** Replicates byte reader functionality in GeoTrellis that we don't get
    * access to
    *
    * @param uri
    * @return
    */
  def getByteReader(uri: String): StreamingByteReader = {

    val javaURI = new URI(uri)
    val noQueryParams =
      URLEncodedUtils.parse(uri, Charset.forName("UTF-8")).isEmpty

    val rr = javaURI.getScheme match {
      case null =>
        FileRangeReader(Paths.get(uri).toFile)

      case "file" =>
        FileRangeReader(Paths.get(javaURI).toFile)

      case "http" | "https" if noQueryParams =>
        HttpRangeReader(new URL(uri))

      case "http" | "https" =>
        new HttpRangeReader(new URL(uri), false)

      case "s3" =>
        val s3Uri = new AmazonS3URI(java.net.URLDecoder.decode(uri, "UTF-8"))
        S3RangeReader(s3Uri.getBucket, s3Uri.getKey, s3Client)

      case scheme =>
        throw new IllegalArgumentException(
          s"Unable to read scheme $scheme at $uri"
        )
    }
    new StreamingByteReader(rr, 128000)
  }

  import GeoTiffReader.geoTiffMultibandTile

  /** Creates Lazy/Streaming MultibandGeoTiff from GeoTiffInfo
    *
    * useful to avoid overhead of reading metadata
    *
    * @param providedInfo
    * @return
    */
  def readMultibandWithInfo(providedInfo: GeoTiffInfo): MultibandGeoTiff = {
    def getMultiband(
        geoTiffTile: GeoTiffMultibandTile,
        info: GeoTiffInfo
    ): MultibandGeoTiff = {

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
    * and any overviews in order to recreate GeoTiffInfo that
    * the reader needs
    *
    * @param byteReader
    * @param withOverviews
    * @return
    */
  def getAllTiffTags(
      byteReader: ByteReader,
      withOverviews: Boolean
  ): List[TiffTags] = {
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
            TiffTags.read(byteReader, smallStart.toLong)(
              IntTiffTagOffsetSize
            )
          case _ =>
            byteReader.position(8)
            val bigStart = byteReader.getLong
            TiffTags.read(byteReader, bigStart)(LongTiffTagOffsetSize)
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
                val ifdTiffTags =
                  TiffTags.read(byteReader, ifdOffset)(IntTiffTagOffsetSize)
                // TIFF Reader supports only overviews at this point
                // Overview is a reduced-resolution IFD
                val subfileType = ifdTiffTags.nonBasicTags.newSubfileType
                  .flatMap(NewSubfileType.fromCode)
                if (subfileType.contains(ReducedImage))
                  tiffTagsBuffer += ifdTiffTags
                ifdOffset = byteReader.getInt
              }
            case _ =>
              var ifdOffset = byteReader.getLong
              while (ifdOffset > 0) {
                val ifdTiffTags =
                  TiffTags.read(byteReader, ifdOffset)(LongTiffTagOffsetSize)
                // TIFF Reader supports only overviews at this point
                // Overview is a reduced-resolution IFD
                val subfileType = ifdTiffTags.nonBasicTags.newSubfileType
                  .flatMap(NewSubfileType.fromCode)
                if (subfileType.contains(ReducedImage))
                  tiffTagsBuffer += ifdTiffTags
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

  /** Helper function that gets a serializable GeoTiffInfo given
    * a URI
    *
    * @param uri
    * @return
    */
  def getGeotiffInfo(uri: String): BacksplashGeoTiffInfo = {
    val reader = getByteReader(uri)
    val geoTiffInfo = GeoTiffInfo.read(reader, true, true)
    val tiffTags = NEL.fromListUnsafe(getAllTiffTags(reader, true))
    BacksplashGeoTiffInfo.fromGeotiffInfo(geoTiffInfo, tiffTags)
  }

}
