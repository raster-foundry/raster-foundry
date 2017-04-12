package com.azavea.rf.batch.export.model

import com.azavea.rf.batch.BatchSpec
import com.azavea.rf.datamodel._

import io.circe.parser._
import io.circe.syntax._

import geotrellis.proj4.CRS
import geotrellis.vector._
import geotrellis.vector.io._

import cats.implicits._

import org.scalatest._

import java.net.URI
import java.util.UUID

class ExportDefinitionSpec extends FunSpec with Matchers with BatchSpec {
  it("Print Export definition") {
    val expected = ExportDefinition(
      id = UUID.fromString("dda6080f-f7ad-455d-b409-764dd8c57039"),
      input = InputDefinition(
        projectId = UUID.fromString("dda6080f-f7ad-455d-b409-764dd8c57036"),
        resolution = 15,
        layers = Array(
          ExportLayerDefinition(
            layerId = UUID.fromString("8436f7e9-b7f7-4d4f-bda8-76b32c356cff"),
            ingestLocation = new URI("s3://test/"),
            colorCorrections = Some(
              ColorCorrect.Params(
                redBand = 0,
                greenBand = 1,
                blueBand = 2,
                redGamma = Some(1d),
                greenGamma = Some(2d),
                blueGamma = Some(3d),
                contrast = Some(4d),
                brightness = Some(5),
                alpha = Some(6d),
                beta = Some(7d),
                min = Some(8),
                max = Some(9),
                equalize = true
              )
            )
          )
        ),
        mask = Some(
          """
            |{
            |        "type":"MultiPolygon",
            |        "coordinates":[
            |            [
            |                [
            |                    [
            |                        -78.91337999999999,
            |                        -12.25582
            |                    ],
            |                    [
            |                        -77.20025,
            |                        -12.620750000000017
            |                    ],
            |                    [
            |                        -76.82601,
            |                        -10.880060000000018
            |                    ],
            |                    [
            |                        -78.52844,
            |                        -10.519079999999995
            |                    ],
            |                    [
            |                        -78.91337999999999,
            |                        -12.25582
            |                    ]
            |                ]
            |            ]
            |        ]
            |    }
          """.stripMargin.parseGeoJson[MultiPolygon]())
      ),
      output = OutputDefinition(
        crs = Some(CRS.fromEpsgCode(32654)),
        rasterSize = Some(256),
        render = Some(Render(operation = "id", bands = Some(Array(1, 2, 3)))),
        crop = false,
        stitch = false,
        source = new URI("s3://test/")
      )
    )

    val actual =
      decode[ExportDefinition](getJson("/export/localJob.json")).toOption match {
        case Some(ed) => ed
        case _ => throw new Exception("Incorrect json to parse")
      }

    expected.asJson shouldBe actual.asJson
  }
}

