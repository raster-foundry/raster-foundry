package com.azavea.rf.export.model

import geotrellis.proj4.CRS
import geotrellis.vector._
import geotrellis.vector.io._

import spray.json._
import org.scalatest._

import java.net.URI
import java.util.UUID

class ExportDefinitionSpec extends FunSpec with Matchers {
  def getJson(resource: String): String = {
    val stream = getClass.getResourceAsStream(resource)
    val lines = scala.io.Source.fromInputStream(stream).getLines
    val json = lines.mkString(" ")
    stream.close()
    json
  }

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
            attributes = List()
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
          """.stripMargin.parseJson.convertTo[MultiPolygon]
        )
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

    val actual = getJson("/localJob.json").parseJson.convertTo[ExportDefinition]

    expected.toJson shouldBe actual.toJson
  }
}

