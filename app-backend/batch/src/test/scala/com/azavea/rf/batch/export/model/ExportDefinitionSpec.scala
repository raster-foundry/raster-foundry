package com.rasterfoundry.batch.export.model

import com.rasterfoundry.batch.BatchSpec
import com.rasterfoundry.datamodel._
import com.rasterfoundry.datamodel.color._
import com.rasterfoundry.tool.ast.MapAlgebraAST._
import com.rasterfoundry.tool.ast._

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
  val outDef = OutputDefinition(
    crs = Some(CRS.fromEpsgCode(32654)),
    rasterSize = Some(256),
    render = Some(Render(operation = "id", bands = Some(Array(1, 2, 3)))),
    crop = false,
    source = new URI("s3://test/"),
    dropboxCredential = None
  )

  it("SimpleInput") {
    val cc = ColorCorrect.Params(
      redBand = 0,
      greenBand = 1,
      blueBand = 2,
      bandClipping = PerBandClipping(
        enabled = true,
        redMax = Some(9),
        greenMax = Some(9),
        blueMax = Some(9),
        redMin = None,
        greenMin = None,
        blueMin = None
      ),
      gamma = BandGamma(
        enabled = true,
        redGamma = Some(1d),
        greenGamma = Some(2d),
        blueGamma = Some(3d)
      ),
      sigmoidalContrast = SigmoidalContrast(
        enabled = true,
        alpha = Some(6d),
        beta = Some(7d)
      ),
      tileClipping = MultiBandClipping(
        enabled = true,
        min = Some(8),
        max = Some(9)
      ),
      saturation = Saturation(
        enabled = true,
        saturation = Some(1.0)
      ),
      equalize = Equalization(
        enabled = true
      ),
      autoBalance = AutoWhiteBalance(
        enabled = false
      )
    )

    val mask = """
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
          """.stripMargin.parseGeoJson[MultiPolygon]()

    val expected = ExportDefinition(
      id = UUID.fromString("dda6080f-f7ad-455d-b409-764dd8c57039"),
      input = InputDefinition(
        resolution = 15,
        style = SimpleInput(
          layers = Array(
            MosaicDefinition(
              sceneId = UUID.fromString("8436f7e9-b7f7-4d4f-bda8-76b32c356cff"),
              ingestLocation = Some("s3://test/"),
              colorCorrections = cc,
              sceneType = Some(SceneType.Avro),
              footprint = Some(mask),
              isSingleBand = false,
              singleBandOptions = Some(().asJson)
            )),
          mask = Some(mask)
        )
      ),
      output = outDef
    )

    val actual =
      decode[ExportDefinition](getJson("/export/localJob.json")) match {
        case Right(ed) => ed
        case Left(e)   => throw e
      }

    expected.asJson shouldBe actual.asJson
  }

  it("ASTInput isomorphism (scene nodes)") {
    val s0 = SceneRaster(UUID.randomUUID, UUID.randomUUID, Some(5), None, None)
    val s1 = SceneRaster(UUID.randomUUID, UUID.randomUUID, Some(5), None, None)
    val ast: MapAlgebraAST = Addition(List(s0, s1), UUID.randomUUID, None)

    val inDef = InputDefinition(
      15,
      ASTInput(
        ast,
        ast.tileSources.map(_ => UUID.randomUUID -> "s3://foo/bar/").toMap,
        Map.empty))
    val ed = ExportDefinition(
      UUID.fromString("dda6080f-f7ad-455d-b409-764dd8c57039"),
      inDef,
      outDef
    )

    decode[ExportDefinition](ed.asJson.spaces2) match {
      case Right(ed2) => ed2 shouldBe ed
      case Left(err)  => throw new Exception(s"EXDEF: ${err}")
    }
  }

  it("ASTInput isomorphism (project nodes)") {
    val s0 =
      ProjectRaster(UUID.randomUUID, UUID.randomUUID, Some(5), None, None)
    val s1 =
      ProjectRaster(UUID.randomUUID, UUID.randomUUID, Some(5), None, None)
    val ast: MapAlgebraAST = Addition(List(s0, s1), UUID.randomUUID, None)

    val astIn = ASTInput(
      ast,
      Map.empty,
      ast.tileSources
        .map({ src =>
          src.id -> List(
            (UUID.randomUUID, "s3://foo/bar/1"),
            (UUID.randomUUID, "s3://foo/bar/2"),
            (UUID.randomUUID, "s3://foo/bar/3")
          )
        })
        .toMap
    )

    val inDef = InputDefinition(
      15,
      astIn
    )

    val ed = ExportDefinition(
      UUID.fromString("dda6080f-f7ad-455d-b409-764dd8c57039"),
      inDef,
      outDef
    )

    decode[ExportDefinition](ed.asJson.spaces2) match {
      case Right(ed2) => ed2 shouldBe ed
      case Left(err)  => throw new Exception(s"EXDEF: ${err}")
    }
  }
}
