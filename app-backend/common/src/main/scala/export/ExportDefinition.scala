package com.rasterfoundry.common.export

import _root_.io.circe._
import _root_.io.circe.syntax._
import com.azavea.maml.ast._
import geotrellis.proj4.WebMercator
import geotrellis.vector._

import java.util.UUID

// The information necessary to export a tif
final case class ExportDefinition[SourceDefinition](
    id: UUID,
    source: SourceDefinition,
    output: OutputDefinition
)

object ExportDefinition {

  implicit def encodeExportDefinition[SourceDefinition: Encoder]
    : Encoder[ExportDefinition[SourceDefinition]] =
    new Encoder[ExportDefinition[SourceDefinition]] {
      def apply(exportDef: ExportDefinition[SourceDefinition]): Json =
        Json.obj(
          ("id", exportDef.id.asJson),
          ("src", exportDef.source.asJson),
          ("output", exportDef.output.asJson)
        )
    }

  implicit def decodeExportDefinition[SourceDefinition: Decoder]
    : Decoder[ExportDefinition[SourceDefinition]] =
    new Decoder[ExportDefinition[SourceDefinition]] {
      def apply(
          c: HCursor): Decoder.Result[ExportDefinition[SourceDefinition]] =
        for {
          id <- c.downField("id").as[UUID]
          src <- c.downField("src").as[SourceDefinition]
          out <- c.downField("output").as[OutputDefinition]
        } yield {
          ExportDefinition[SourceDefinition](id, src, out)
        }
    }

  private val outputDefinition =
    OutputDefinition(
      Some(WebMercator),
      "file:///tmp/test.tif",
      Some("myDropboxCreds")
    )
  def mockMosaic = {
    val source = MosaicExportSource(
      1,
      MultiPolygon(Extent(0, 0, 1, 1).toPolygon),
      List(
        ("file:///tmp/test/source1.tif", List(1, 2), Some(0)),
        ("file:///tmp/test/source2.tif", List(2, 3), Some(0)),
        ("file:///tmp/test/source3.tif", List(3, 4), Some(0))
      )
    )
    ExportDefinition(UUID.randomUUID(), source, outputDefinition)
  }

  def mockAnalysis = {
    val outputDefinition =
      OutputDefinition(
        Some(WebMercator),
        "file:///tmp/test.tif",
        Some("myDropboxCreds")
      )
    val source = AnalysisExportSource(
      1,
      MultiPolygon(Extent(0, 0, 1, 1).toPolygon),
      Addition(List(RasterVar("mockAST1"), RasterVar("mockAST2"))),
      Map(
        "mockAST1" -> List(("file:///tmp/test/source1.tif", 1, Some(0)),
                           ("file:///tmp/test/source2.tif", 1, Some(0)),
                           ("file:///tmp/test/source3.tif", 1, Some(0))),
        "mockAST2" -> List(("file:///tmp/test/source1.tif", 1, Some(0)),
                           ("file:///tmp/test/source2.tif", 1, Some(0)),
                           ("file:///tmp/test/source3.tif", 1, Some(0)))
      )
    )
    ExportDefinition(UUID.randomUUID(), source, outputDefinition)
  }
}
