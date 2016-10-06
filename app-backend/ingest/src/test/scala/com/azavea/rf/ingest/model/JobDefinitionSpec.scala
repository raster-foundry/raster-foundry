package com.azavea.rf.ingest.model

import spray.json._
import org.scalatest._

import com.azavea.rf.util.Resource

class JobDefinitionSpec extends WordSpec with Matchers {
  "The JobDefinition model" should {

    "be round trip json-serializable - form 1" in {
      val json = Resource("job-definition-1.json").parseJson
      val jobDefinition = json.convertTo[JobDefinition]
      jobDefinition.toJson shouldEqual json
    }

    "be round trip json-serializable - form 2" in {
      val json = Resource("job-definition-2.json").parseJson
      val jobDefinition = json.convertTo[JobDefinition]
      jobDefinition.toJson shouldEqual json
    }

    "be round trip json-serializable - form 3" in {
      val json = Resource("job-definition-3.json").parseJson
      val jobDefinition = json.convertTo[JobDefinition]
      jobDefinition.toJson shouldEqual json
    }

    "be round trip json-serializable - form 4" in {
      val json = Resource("job-definition-4.json").parseJson
      val jobDefinition = json.convertTo[JobDefinition]
      jobDefinition.toJson shouldEqual json
    }

    "deserialize from a degenerate case of json form 4" in {
      val json1 = Resource("job-definition-4.json").parseJson
      val jobDefinition1 = json1.convertTo[JobDefinition]

      val json2 = Resource("job-definition-4.json").parseJson
      val jobDefinition2 = json2.convertTo[JobDefinition]

      jobDefinition1.toJson shouldEqual jobDefinition2.toJson
    }

    "print all urls" in {
      val json = Resource("job-definition-2.json").parseJson
      val jobDefinition = json.convertTo[JobDefinition]

      val scene = jobDefinition.scenes.head
      println(scene.zipped.head._1, scene.zipped.head._2)

      val imgSrc = scene.sources.head
      imgSrc.getHttpUrls.head._3.foreach(println(_))
    }
  }
}
