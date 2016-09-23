package com.azavea.rf

import spray.json._
import org.scalatest._

class JobDefinitionSpec extends WordSpec with Matchers {
  "JobDefinitions" should {
    "be json-readable and writable" in {
      val json = """{"scenes":[{"output":"s3://raster-foundary/userName/layerName","source":{"extent":[0,0,10,10],"urls":["s3://bucket/landsat/LC8_file1.tif","s3://bucket/landsat/LC8_file2.tif","s3://bucket/landsat/LC8_file3.tif"]}}]}""".parseJson
      val jobDefinition = json.convertTo[JobDefinition]
      jobDefinition.toJson shouldEqual json
    }
  }
}
