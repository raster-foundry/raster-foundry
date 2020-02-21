package com.rasterfoundry

import com.rasterfoundry.datamodel._

import _root_.io.circe._
import _root_.io.circe.generic.semiauto._
import _root_.io.circe.parser._
import _root_.io.circe.syntax._
import cats.syntax.either._
import geotrellis.proj4.CRS
import geotrellis.raster._
import geotrellis.raster.io.geotiff._
import geotrellis.raster.io.geotiff.compression._
import geotrellis.raster.io.geotiff.tags._
import geotrellis.raster.render.IndexedColorMap
import geotrellis.vector._
import geotrellis.vector.io.json.{Implicits => GeoJsonImplicits}
import geotrellis.vector.{Extent, MultiPolygon}

import scala.util.Try

import java.nio.ByteOrder

package object common extends GeoJsonImplicits {

  implicit val crsEncoder: Encoder[CRS] =
    Encoder.encodeString.contramap[CRS] { crs =>
      crs.epsgCode
        .map { c =>
          s"epsg:$c"
        }
        .getOrElse(crs.toProj4String)
    }

  implicit val crsDecoder: Decoder[CRS] =
    Decoder.decodeString.emap { str =>
      Either
        .catchNonFatal(Try(CRS.fromName(str)) getOrElse CRS.fromString(str))
        .leftMap(_ => "CRS")
    }

  implicit val extentEncoder: Encoder[Extent] =
    new Encoder[Extent] {
      def apply(extent: Extent): Json =
        List(extent.xmin, extent.ymin, extent.xmax, extent.ymax).asJson
    }
  implicit val extentDecoder: Decoder[Extent] =
    Decoder[Json] emap { js =>
      js.as[List[Double]]
        .map {
          case List(xmin, ymin, xmax, ymax) =>
            Extent(xmin, ymin, xmax, ymax)
        }
        .leftMap(_ => "Extent")
    }

  implicit val multipolygonEncoder: Encoder[MultiPolygon] =
    new Encoder[MultiPolygon] {
      def apply(mp: MultiPolygon): Json = {
        parse(mp.toGeoJson) match {
          case Right(js: Json) => js
          case Left(e)         => throw e
        }
      }
    }

  implicit val multipolygonDecoder: Decoder[MultiPolygon] = Decoder[Json] map {
    _.spaces4.parseGeoJson[MultiPolygon]
  }

  // Decoders
  implicit val byteOrderDecoder: Decoder[ByteOrder] =
    Decoder.decodeString.emap {
      case "BIG_ENDIAN"    => Either.right(ByteOrder.BIG_ENDIAN)
      case "LITTLE_ENDIAN" => Either.right(ByteOrder.LITTLE_ENDIAN)
      case s               => Either.left(s"Unknown Byte Order: $s")
    }
  implicit val byteOrderEncoder: Encoder[ByteOrder] =
    Encoder.encodeString.contramap(_.toString)
  implicit val metadataTagsDecoder: Decoder[MetadataTags] =
    deriveDecoder[MetadataTags]
  implicit val basicTagsDecoder: Decoder[BasicTags] = deriveDecoder[BasicTags]
  implicit val nonBasicTagsDecoder: Decoder[NonBasicTags] =
    deriveDecoder[NonBasicTags]
  implicit val pixel3DTagsDecoder: Decoder[Pixel3D] = deriveDecoder[Pixel3D]
  implicit val configKeysDecoder: Decoder[ConfigKeys] =
    deriveDecoder[ConfigKeys]
  implicit val geogCSParameterKeysDecoder: Decoder[GeogCSParameterKeys] =
    deriveDecoder[GeogCSParameterKeys]
  implicit val projectedFalsingsDecoder: Decoder[ProjectedFalsings] =
    deriveDecoder[ProjectedFalsings]
  implicit val projectedCSParameterKeysDecoder
    : Decoder[ProjectedCSParameterKeys] =
    deriveDecoder[ProjectedCSParameterKeys]
  implicit val verticalCSKeysDecoder: Decoder[VerticalCSKeys] =
    deriveDecoder[VerticalCSKeys]
  implicit val nonStandardizedKeysDecoder: Decoder[NonStandardizedKeys] =
    deriveDecoder[NonStandardizedKeys]
  implicit val geoKeyDirectoryTagsDecoder: Decoder[GeoKeyDirectory] =
    deriveDecoder[GeoKeyDirectory]
  implicit val geotiffTagsDecoder: Decoder[GeoTiffTags] =
    deriveDecoder[GeoTiffTags]
  implicit val docTagsDecoder: Decoder[DocumentationTags] =
    deriveDecoder[DocumentationTags]
  implicit val tileTagsDecoder: Decoder[TileTags] = deriveDecoder[TileTags]
  implicit val cmykTagsDecoder: Decoder[CmykTags] = deriveDecoder[CmykTags]
  implicit val dataSampleFormatTagsDecoder: Decoder[DataSampleFormatTags] =
    deriveDecoder[DataSampleFormatTags]
  implicit val colimetryTagsDecoder: Decoder[ColimetryTags] =
    deriveDecoder[ColimetryTags]
  implicit val jpegTagsDecoder: Decoder[JpegTags] = deriveDecoder[JpegTags]
  implicit val ycbcrTagsDecoder: Decoder[YCbCrTags] = deriveDecoder[YCbCrTags]
  implicit val nonStandardizedTagsDecoder: Decoder[NonStandardizedTags] =
    deriveDecoder[NonStandardizedTags]
  implicit val tiffDecoder: Decoder[TiffType] = deriveDecoder[TiffType]
  implicit val tiffTagsDecoder: Decoder[TiffTags] = deriveDecoder[TiffTags]

  implicit val storageMethodDecoder: Decoder[StorageMethod] =
    new Decoder[StorageMethod] {
      final def apply(c: HCursor): Decoder.Result[StorageMethod] = {
        for {
          storageType <- c.downField("storageType").as[String]
        } yield {
          storageType match {
            case "striped" => {
              val rowsPerStrip =
                c.downField("rowsPerStrip").as[Option[Int]] match {
                  case Left(_)  => None
                  case Right(v) => v
                }
              new Striped(rowsPerStrip)
            }
            case _ => {
              val cols = c.downField("cols").as[Int] match {
                case Left(_)  => 256
                case Right(v) => v
              }
              val rows = c.downField("rows").as[Int] match {
                case Left(_)  => 256
                case Right(v) => v
              }
              Tiled(cols, rows)
            }
          }
        }
      }
    }

  implicit val storageMethodEncoder: Encoder[StorageMethod] =
    new Encoder[StorageMethod] {
      final def apply(a: StorageMethod): Json = a match {
        case _: Striped => Json.obj(("storageType", Json.fromString("striped")))
        case Tiled(cols, rows) =>
          Json.obj(("storageType", Json.fromString("tiled")),
                   ("cols", Json.fromInt(cols)),
                   ("rows", Json.fromInt(rows)))
      }
    }

  implicit val compressionDecoder: Decoder[Compression] =
    new Decoder[Compression] {
      final def apply(c: HCursor): Decoder.Result[Compression] = {
        for {
          compressionType <- c.downField("compressionType").as[String]
        } yield {
          compressionType match {
            case "NoCompression" => NoCompression
            case _ => {
              c.downField("level").as[Int] match {
                case Left(_)  => DeflateCompression()
                case Right(i) => DeflateCompression(i)
              }
            }
          }
        }
      }
    }

  implicit val compressionEncoder: Encoder[Compression] =
    new Encoder[Compression] {
      final def apply(a: Compression): Json = a match {
        case NoCompression =>
          Json.obj(("compressionType", Json.fromString("NoCompression")))
        case d: DeflateCompression =>
          Json.obj(("compressionType", Json.fromString("Deflate")),
                   ("level", Json.fromInt(d.level)))
      }
    }

  implicit val indexedColorMapDecoder: Decoder[IndexedColorMap] =
    Decoder.decodeSeq[Int].emap { s =>
      Either.right(new IndexedColorMap(s))
    }
  implicit val indexedColorMapEncoder: Encoder[IndexedColorMap] =
    Encoder.encodeSeq[Int].contramapArray(_.colors.toSeq)

  implicit val newSubFileTypeDecoder: Decoder[NewSubfileType] =
    deriveDecoder[NewSubfileType]

  implicit val geotiffOptionsDecoder: Decoder[GeoTiffOptions] =
    deriveDecoder[GeoTiffOptions]

  implicit val bandTypeDecoder: Decoder[BandType] =
    new Decoder[BandType] {
      final def apply(c: HCursor): Decoder.Result[BandType] = {
        for {
          bitsPerSample <- c.downField("bitsPerSample").as[Int]
          sampleFormat <- c.downField("sampleFormat").as[Int]
        } yield {
          BandType(bitsPerSample, sampleFormat)
        }
      }
    }

  implicit val bandTypeEncoder: Encoder[BandType] = new Encoder[BandType] {
    final def apply(a: BandType): Json = Json.obj(
      ("bitsPerSample", Json.fromInt(a.bitsPerSample)),
      ("sampleFormat", Json.fromInt(a.sampleFormat))
    )
  }

  implicit val tileLayoutDecoder: Decoder[TileLayout] =
    deriveDecoder[TileLayout]
  implicit val geotiffSegmentLayoutDecoder: Decoder[GeoTiffSegmentLayout] =
    deriveDecoder[GeoTiffSegmentLayout]

  implicit val tagsDecoder: Decoder[Tags] = deriveDecoder[Tags]
  implicit val bsgtDecoder: Decoder[BacksplashGeoTiffInfo] =
    deriveDecoder[BacksplashGeoTiffInfo]

  // Encoders

  implicit val metadataTagsEncoder: Encoder[MetadataTags] =
    deriveEncoder[MetadataTags]
  implicit val basicTagsEncoder: Encoder[BasicTags] = deriveEncoder[BasicTags]
  implicit val nonBasicTagsEncoder: Encoder[NonBasicTags] =
    deriveEncoder[NonBasicTags]
  implicit val pixel3DTagsEncoder: Encoder[Pixel3D] = deriveEncoder[Pixel3D]
  implicit val configKeysEncoder: Encoder[ConfigKeys] =
    deriveEncoder[ConfigKeys]
  implicit val geogCSParameterKeysEncoder: Encoder[GeogCSParameterKeys] =
    deriveEncoder[GeogCSParameterKeys]
  implicit val projectedFalsingsEncoder: Encoder[ProjectedFalsings] =
    deriveEncoder[ProjectedFalsings]
  implicit val projectedCSParameterKeysEncoder
    : Encoder[ProjectedCSParameterKeys] =
    deriveEncoder[ProjectedCSParameterKeys]
  implicit val verticalCSKeysEncoder: Encoder[VerticalCSKeys] =
    deriveEncoder[VerticalCSKeys]
  implicit val nonStandardizedKeysEncoder: Encoder[NonStandardizedKeys] =
    deriveEncoder[NonStandardizedKeys]
  implicit val geoKeyDirectoryTagsEncoder: Encoder[GeoKeyDirectory] =
    deriveEncoder[GeoKeyDirectory]
  implicit val geotiffTagsEncoder: Encoder[GeoTiffTags] =
    deriveEncoder[GeoTiffTags]
  implicit val docTagsEncoder: Encoder[DocumentationTags] =
    deriveEncoder[DocumentationTags]
  implicit val tileTagsEncoder: Encoder[TileTags] = deriveEncoder[TileTags]
  implicit val cmykTagsEncoder: Encoder[CmykTags] = deriveEncoder[CmykTags]
  implicit val dataSampleFormatTagsEncoder: Encoder[DataSampleFormatTags] =
    deriveEncoder[DataSampleFormatTags]
  implicit val colimetryTagsEncoder: Encoder[ColimetryTags] =
    deriveEncoder[ColimetryTags]
  implicit val jpegTagsEncoder: Encoder[JpegTags] = deriveEncoder[JpegTags]
  implicit val ycbcrTagsEncoder: Encoder[YCbCrTags] = deriveEncoder[YCbCrTags]
  implicit val nonStandardizedTagsEncoder: Encoder[NonStandardizedTags] =
    deriveEncoder[NonStandardizedTags]
  implicit val tiffEncoder: Encoder[TiffType] = deriveEncoder[TiffType]
  implicit val tiffTagsEncoder: Encoder[TiffTags] = deriveEncoder[TiffTags]

  implicit val interleaveMethodEncoder: Encoder[InterleaveMethod] =
    Encoder.encodeString.contramap[InterleaveMethod](_.toString)

  implicit val interleaveMethodDecoder: Decoder[InterleaveMethod] =
    Decoder.decodeString.emap {
      case "PixelInterleave" => Right(PixelInterleave)
      case "BandInterleave"  => Right(BandInterleave)
      case str               => Left(s"Invalid Interleave: $str")
    }

  implicit val newSubFileTypeEncoder: Encoder[NewSubfileType] =
    deriveEncoder[NewSubfileType]

  implicit val geotiffOptionsEncoder: Encoder[GeoTiffOptions] =
    deriveEncoder[GeoTiffOptions]

  implicit val tileLayoutEncoder: Encoder[TileLayout] =
    deriveEncoder[TileLayout]
  implicit val geotiffSegmentLayoutEncoder: Encoder[GeoTiffSegmentLayout] =
    deriveEncoder[GeoTiffSegmentLayout]

  implicit val tagsEncoder: Encoder[Tags] = deriveEncoder[Tags]
  implicit val bsgtEncoder: Encoder[BacksplashGeoTiffInfo] =
    deriveEncoder[BacksplashGeoTiffInfo]
}
