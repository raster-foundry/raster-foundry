package com.azavea.rf

import spray.json._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport

import geotrellis.vector.Extent
import geotrellis.vector.io._

import java.net.URL

/** Each TileSource, which sits the bottom of any JobDefinition (see below) is made of tiff urls and a target extent */
case class TileSource(urls: Array[String], extent: Extent)

object TileSource {
  implicit object TileSourceJsonFormat extends RootJsonFormat[TileSource] {
    def write(source: TileSource): JsValue =
      JsObject(
        "urls" -> JsArray(source.urls.map(JsString(_)).toVector),
        "extent" -> JsArray(
          JsNumber(source.extent.xmin),
          JsNumber(source.extent.ymin),
          JsNumber(source.extent.xmax),
          JsNumber(source.extent.ymax)
        )
      )
    def read(value: JsValue): TileSource = value.asJsObject.getFields("urls", "extent") match {
      case Seq(JsArray(urls), JsArray(extent)) =>
        assert(extent.size == 4)
        val u: Array[String] = urls.map({
            case JsString(url) => url
            case _ => deserializationError("Failed to parse a url string tilesource")
        }).toArray
        val e = extent.map({
          case JsNumber(minmax) => minmax.toDouble
          case _ => deserializationError("Failed to parse a number in extent array")
        })
        TileSource(u, Extent(e(0), e(1), e(2), e(3)))
    }
  }
}

/** Each [[JobDefinition]] (see below) is made up of arbitrarily many scenes which will be output somewhere */
case class Scene(output: String, source: TileSource)

object Scene extends DefaultJsonProtocol {
  implicit val SceneJsonFormat = jsonFormat2(Scene.apply)
}

/** The top level abstraction for defining an ingest job */
case class JobDefinition(scenes: Array[Scene])

object JobDefinition extends DefaultJsonProtocol {
  implicit val JobDefinitionJsonFormat = jsonFormat1(JobDefinition.apply)
}

