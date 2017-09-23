package com.azavea.rf.batch.sentinel2

import com.azavea.rf.batch.BatchSpec

import io.circe._
import io.circe.parser._
import cats.implicits._
import geotrellis.proj4.CRS
import geotrellis.vector._
import geotrellis.vector.io._

import org.scalatest.{FunSpec, Matchers}

@SuppressWarnings(Array("OptionGet"))
class TileInfoSpec extends FunSpec with Matchers with BatchSpec {

  it("Should parse sentinel2 tileInfo.json") {
    val json: Option[Json] = parse(getJson("/sentinel2/tileInfo.json")).toOption

    val tileGeometry =
      """
        |{
        |    "type": "Polygon",
        |    "crs": {
        |      "type": "name",
        |      "properties": {
        |        "name": "urn:ogc:def:crs:EPSG:8.8.1:32601"
        |      }
        |    },
        |    "coordinates": [
        |      [
        |        [
        |          300000,
        |          8100000
        |        ],
        |        [
        |          409800,
        |          8100000
        |        ],
        |        [
        |          409800,
        |          7990200
        |        ],
        |        [
        |          300000,
        |          7990200
        |        ],
        |        [
        |          300000,
        |          8100000
        |        ]
        |      ]
        |    ]
        |  }
      """.stripMargin.parseGeoJson[Polygon].reproject(CRS.fromName("EPSG:32601"), CRS.fromName("EPSG:3857"))

    val tileDataGeometry =
      """
        |{
        |    "type": "Polygon",
        |    "crs": {
        |      "type": "name",
        |      "properties": {
        |        "name": "urn:ogc:def:crs:EPSG:8.8.1:32601"
        |      }
        |    },
        |    "coordinates": [
        |      [
        |        [
        |          309960.541747247,
        |          8077156.668410842
        |        ],
        |        [
        |          309796.059525847,
        |          8076904.236719294
        |        ],
        |        [
        |          329066.612015148,
        |          8064562.18476013
        |        ],
        |        [
        |          329085.568534509,
        |          8064588.498491946
        |        ],
        |        [
        |          329299.367978034,
        |          8064451.342706096
        |        ],
        |        [
        |          329400.884088736,
        |          8064599.761024376
        |        ],
        |        [
        |          350531.865227959,
        |          8050661.626307452
        |        ],
        |        [
        |          350386.398195334,
        |          8050436.741314584
        |        ],
        |        [
        |          369182.051979401,
        |          8037869.623100078
        |        ],
        |        [
        |          369384.709064725,
        |          8037746.164060908
        |        ],
        |        [
        |          369514.938393351,
        |          8037938.807535538
        |        ],
        |        [
        |          390353.859082776,
        |          8023723.146221532
        |        ],
        |        [
        |          390220.997324456,
        |          8023519.608218648
        |        ],
        |        [
        |          408838.384896601,
        |          8010542.663425412
        |        ],
        |        [
        |          408854.425333288,
        |          8010564.59076652
        |        ],
        |        [
        |          409031.285171853,
        |          8010441.379139354
        |        ],
        |        [
        |          409052.521514721,
        |          8010470.98510298
        |        ],
        |        [
        |          409067.004846534,
        |          8010462.143478204
        |        ],
        |        [
        |          409154.885669833,
        |          8010592.128718286
        |        ],
        |        [
        |          409799,
        |          8010139.511942504
        |        ],
        |        [
        |          409799,
        |          7990201
        |        ],
        |        [
        |          300001,
        |          7990201
        |        ],
        |        [
        |          300001,
        |          8083510.381888526
        |        ],
        |        [
        |          309960.541747247,
        |          8077156.668410842
        |        ]
        |      ]
        |    ]
        |  }
      """.stripMargin.parseGeoJson[Polygon].reproject(CRS.fromName("EPSG:32601"), CRS.fromName("EPSG:3857"))

    val sceneMetadata =
      Map(
        "path"                   -> "tiles/1/X/CA/2017/4/20/0",
        "cloudyPixelPercentage"  -> "12.96",
        "gridSquare"             -> "CA",
        "timeStamp"              -> "2017-04-20T00:26:08.456Z",
        "utmZone"                -> "1",
        "productPath"            -> "products/2017/4/20/S2A_MSIL1C_20170420T002611_N0204_R102_T01XCA_20170420T002608",
        "productName"            -> "S2A_MSIL1C_20170420T002611_N0204_R102_T01XCA_20170420T002608",
        "latitudeBand"           -> "X",
        "dataCoveragePercentage" -> "51.98"
      )

    json.map { tileInfo =>
      ImportSentinel2.multiPolygonFromJson(tileInfo, "tileDataGeometry").get.vertices shouldBe MultiPolygon(tileDataGeometry).vertices
      ImportSentinel2.multiPolygonFromJson(tileInfo, "tileGeometry").get shouldBe MultiPolygon(tileGeometry)

      ImportSentinel2.getSceneMetadata(tileInfo) shouldBe sceneMetadata
    }
  }
}
