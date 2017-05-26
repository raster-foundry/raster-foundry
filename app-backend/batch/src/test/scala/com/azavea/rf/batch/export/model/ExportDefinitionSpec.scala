package com.azavea.rf.batch.export.model

import com.azavea.rf.batch.BatchSpec
import com.azavea.rf.datamodel._
import com.azavea.rf.datamodel.color._

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
        ast = ???, // TODO: Death! Bad!
        params = ???,
        colorCorrections = Some(
          ColorCorrect.Params(
            redBand = 0,
            greenBand = 1,
            blueBand = 2,
            bandClipping = PerBandClipping(
              enabled = true,
              redMax = Some(9), greenMax = Some(9), blueMax = Some(9),
              redMin = None, greenMin = None, blueMin = None
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
        )
      ),
      output = OutputDefinition(
        crs = Some(CRS.fromEpsgCode(32654)),
        rasterSize = Some(256),
        render = Some(Render(operation = "id", bands = Some(Array(1, 2, 3)))),
        crop = false,
        stitch = false,
        source = new URI("s3://test/"),
        dropboxCredential = None
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
