package com.azavea.rf.ingest.model

import spray.json._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.apache.spark.rdd._
import org.apache.spark._
import geotrellis.vector.Extent
import geotrellis.vector.io._

import java.net.URL
import java.util.UUID

/** The top level abstraction for defining an ingest job */
case class JobDefinition(
  name: String,
  id: UUID,
  scenes: Array[Scene]
) {
  def parallelize(implicit sc: SparkContext) = {
    ???
  }
}

object JobDefinition extends DefaultJsonProtocol {
  implicit object JobDefinitionJsonFormat extends RootJsonFormat[JobDefinition] {
    def write(jd: JobDefinition): JsValue = JsObject(
      "name" -> JsString(jd.name),
      "id" -> JsString(jd.id.toString),
      "scenes" -> jd.scenes.toJson
    )
    def read(js: JsValue): JobDefinition = js.asJsObject.getFields("name", "id", "scenes") match {
      case Seq(JsString(name), JsString(id), JsArray(scenesJs)) =>
        val scenes = scenesJs.map(_.convertTo[Scene]).toArray
        JobDefinition(name, UUID.fromString(id), scenes)
      case _ =>
        deserializationError("Failed to parse JobDefinition")
    }
  }
}

