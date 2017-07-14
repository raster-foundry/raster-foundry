package com.azavea.rf.batch.ingest.model

import com.azavea.rf.batch.BatchSpec
import io.circe.parser._
import org.scalatest._

class IngestDefinitionSpec extends FunSpec with Matchers with BatchSpec {
  /** TODO: figure out why the resource loading methods scala provides don't work everywhere */
  val localJson =
    """
      |{
      |  "id": "dda6080f-f7ad-455d-b409-764dd8c57039",
      |  "layers": [
      |    {
      |      "id": "8436f7e9-b7f7-4d4f-bda8-76b32c356cff",
      |      "output": {
      |        "uri": "file:///tmp/landsat-rgb",
      |        "crs": "epsg:3857",
      |        "cellType": "uint16raw",
      |        "histogramBuckets": 256,
      |        "tileSize": 256,
      |        "resampleMethod": "NearestNeighbor",
      |        "keyIndexMethod": "ZCurveKeyIndexMethod",
      |        "pyramid": true,
      |        "native": true
      |      },
      |      "sources": [
      |        {
      |          "uri": "file:///opt/rf/test-resources/imagery/landsat-clipped-B4.tif",
      |          "extent": [
      |            138.8339238,
      |            34.956946,
      |            141.4502449,
      |            37.1094577
      |          ],
      |          "extentCrs": "epsg:4326",
      |          "cellSize": {
      |            "width": 37.15144765106289,
      |            "height": -37.15144765106289
      |          },
      |          "crs": "epsg:32619",
      |          "bandMaps": [
      |            {
      |              "source": 1,
      |              "target": 1
      |            }
      |          ]
      |        },
      |        {
      |          "uri": "file:///opt/rf/test-resources/imagery/landsat-clipped-B3.tif",
      |          "extent": [
      |            138.8339238,
      |            34.956946,
      |            141.4502449,
      |            37.1094577
      |          ],
      |          "extentCrs": "epsg:4326",
      |          "cellSize": {
      |            "width": 37.15144765106289,
      |            "height": -37.15144765106289
      |          },
      |          "crs": "epsg:32619",
      |          "bandMaps": [
      |            {
      |              "source": 1,
      |              "target": 2
      |            }
      |          ]
      |        },
      |        {
      |          "uri": "file:///opt/rf/test-resources/imagery/landsat-clipped-B2.tif",
      |          "extent": [
      |            138.8339238,
      |            34.956946,
      |            141.4502449,
      |            37.1094577
      |          ],
      |          "extentCrs": "epsg:4326",
      |          "cellSize": {
      |            "width": 37.15144765106289,
      |            "height": -37.15144765106289
      |          },
      |          "crs": "epsg:32619",
      |          "bandMaps": [
      |            {
      |              "source": 1,
      |              "target": 3
      |            }
      |          ]
      |        },
      |        {
      |          "uri": "file:///opt/rf/test-resources/imagery/landsat-clipped-B5.tif",
      |          "extent": [
      |            138.8339238,
      |            34.956946,
      |            141.4502449,
      |            37.1094577
      |          ],
      |          "extentCrs": "epsg:4326",
      |          "cellSize": {
      |            "width": 37.15144765106289,
      |            "height": -37.15144765106289
      |          },
      |          "crs": "epsg:32619",
      |          "bandMaps": [
      |            {
      |              "source": 1,
      |              "target": 4
      |            }
      |          ]
      |        }
      |      ]
      |    }
      |  ]
      |}
      |""".stripMargin
  val awsJson =
    """
      |{
      |  "id": "dda6080f-f7ad-455d-b409-764dd8c57039",
      |  "layers": [
      |    {
      |      "id": "8436f7e9-b7f7-4d4f-bda8-76b32c356dff",
      |      "output": {
      |        "ndPattern": { "pattern": { "1": 3.0, "20": 52.3 } },
      |        "uri": "s3://rasterfoundry-staging-catalogs-us-east-1/test",
      |        "crs": "epsg:3857",
      |        "cellType": "uint16raw",
      |        "histogramBuckets": 512,
      |        "tileSize": 256,
      |        "resampleMethod": "NearestNeighbor",
      |        "keyIndexMethod": "ZCurveKeyIndexMethod",
      |        "pyramid": true,
      |        "native": true
      |      },
      |      "sources": [
      |        {
      |          "uri": "http://landsat-pds.s3.amazonaws.com/L8/107/035/LC81070352015218LGN00/LC81070352015218LGN00_B4.TIF",
      |          "extent": [
      |            138.8339238,
      |            34.956946,
      |            141.4502449,
      |            37.1094577
      |          ],
      |          "cellSize": {
      |            "width": 37.15144765106289,
      |            "height": -37.15144765106289
      |          },
      |          "extentCrs": "epsg:4326",
      |          "crs": "epsg:32619",
      |          "bandMaps": [
      |            {
      |              "source": 1,
      |              "target": 1
      |            }
      |          ]
      |        },
      |        {
      |          "uri": "http://landsat-pds.s3.amazonaws.com/L8/107/035/LC81070352015218LGN00/LC81070352015218LGN00_B3.TIF",
      |          "extent": [
      |            138.8339238,
      |            34.956946,
      |            141.4502449,
      |            37.1094577
      |          ],
      |          "cellSize": {
      |            "width": 37.15144765106289,
      |            "height": -37.15144765106289
      |          },
      |          "extentCrs": "epsg:4326",
      |          "crs": "epsg:32619",
      |          "bandMaps": [
      |            {
      |              "source": 1,
      |              "target": 2
      |            }
      |          ]
      |        },
      |        {
      |          "uri": "http://landsat-pds.s3.amazonaws.com/L8/107/035/LC81070352015218LGN00/LC81070352015218LGN00_B2.TIF",
      |          "extent": [
      |            138.8339238,
      |            34.956946,
      |            141.4502449,
      |            37.1094577
      |          ],
      |          "cellSize": {
      |            "width": 37.15144765106289,
      |            "height": -37.15144765106289
      |          },
      |          "extentCrs": "epsg:4326",
      |          "crs": "epsg:32619",
      |          "bandMaps": [
      |            {
      |              "source": 1,
      |              "target": 3
      |            }
      |          ]
      |        },
      |        {
      |          "uri": "http://landsat-pds.s3.amazonaws.com/L8/107/035/LC81070352015218LGN00/LC81070352015218LGN00_B5.TIF",
      |          "extent": [
      |            138.8339238,
      |            34.956946,
      |            141.4502449,
      |            37.1094577
      |          ],
      |          "cellSize": {
      |            "width": 37.15144765106289,
      |            "height": -37.15144765106289
      |          },
      |          "extentCrs": "epsg:4326",
      |          "crs": "epsg:32619",
      |          "bandMaps": [
      |            {
      |              "source": 1,
      |              "target": 4
      |            }
      |          ]
      |        }
      |      ]
      |    }
      |  ]
      |}
      |""".stripMargin

  val awsJsonNoOverrides =
    """
      |{
      |  "id": "dda6080f-f7ad-455d-b409-764dd8c57039",
      |  "layers": [
      |    {
      |      "id": "8436f7e9-b7f7-4d4f-bda8-76b32c356dff",
      |      "output": {
      |        "ndPattern": { "pattern": { "1": 3.0, "20": 52.3 } },
      |        "uri": "s3://geotrellis-test/rf-eac-test",
      |        "crs": "epsg:3857",
      |        "cellType": "uint16raw",
      |        "histogramBuckets": 512,
      |        "tileSize": 256,
      |        "resampleMethod": "NearestNeighbor",
      |        "keyIndexMethod": "ZCurveKeyIndexMethod",
      |        "pyramid": true,
      |        "native": true
      |      },
      |      "sources": [
      |        {
      |          "uri": "http://landsat-pds.s3.amazonaws.com/L8/107/035/LC81070352015218LGN00/LC81070352015218LGN00_B4.TIF",
      |          "bandMaps": [
      |            {
      |              "source": 1,
      |              "target": 1
      |            }
      |          ]
      |        },
      |        {
      |          "uri": "http://landsat-pds.s3.amazonaws.com/L8/107/035/LC81070352015218LGN00/LC81070352015218LGN00_B3.TIF",
      |          "bandMaps": [
      |            {
      |              "source": 1,
      |              "target": 2
      |            }
      |          ]
      |        },
      |        {
      |          "uri": "http://landsat-pds.s3.amazonaws.com/L8/107/035/LC81070352015218LGN00/LC81070352015218LGN00_B2.TIF",
      |          "bandMaps": [
      |            {
      |              "source": 1,
      |              "target": 3
      |            }
      |          ]
      |        },
      |        {
      |          "uri": "http://landsat-pds.s3.amazonaws.com/L8/107/035/LC81070352015218LGN00/LC81070352015218LGN00_B5.TIF",
      |          "bandMaps": [
      |            {
      |              "source": 1,
      |              "target": 4
      |            }
      |          ]
      |        }
      |      ]
      |    }
      |  ]
      |}
    """.stripMargin

  it("parses the sample, aws definition") {
    noException should be thrownBy {
      decode[IngestDefinition](awsJson) match {
        case Right(r) => r
        case _ => throw new Exception("Incorrect IngestDefinition JSON")
      }
    }
  }

  it("parses the sample, aws definition with no overrides") {
    decode[IngestDefinition](awsJsonNoOverrides) match {
      case Right(r) => r
      case _ => throw new Exception("Incorrect IngestDefinition JSON")
    }
  }
}
