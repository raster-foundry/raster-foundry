package com.azavea.rf.ingest.model

import spray.json._
import DefaultJsonProtocol._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport

import geotrellis.vector.Extent

import java.net.URL

/** Each ImageSource, which sits the bottom of any JobDefinition is made of tiff urls,
 *   a target extent, and an optional band parameter.
*/
case class ImageSource(
  urls: Array[String],
  extent: Extent,
  bands: Array[Int] = Array()
)

object ImageSource {
  implicit object ImageSourceJsonFormat extends RootJsonFormat[ImageSource] {
    def write(bs: ImageSource): JsValue = bs.bands.toList match {
      case Nil => // case no bands specified
        JsObject(
          "urls" -> JsArray(bs.urls.map(JsString(_)).toVector),
          "extent" -> bs.extent.toJson
        )
      case _ =>
        JsObject(
          "urls" -> JsArray(bs.urls.map(JsString(_)).toVector),
          "extent" -> bs.extent.toJson,
          "bands" -> JsArray(bs.bands.map(JsNumber(_)).toVector)
        )
    }
    def read(value: JsValue): ImageSource = value match {
      case JsObject(src) if src.keys.toArray.contains("bands") =>
        ImageSource(
          src("urls").convertTo[Array[String]],
          src("extent").convertTo[Extent],
          src("bands").convertTo[Array[Int]]
        )
      case JsObject(src) if src.keys.toArray.contains("band") =>
        ImageSource(
          src("urls").convertTo[Array[String]],
          src("extent").convertTo[Extent],
          Array(src("band").convertTo[Int])
        )
      case JsObject(src) =>
        ImageSource(
          src("urls").convertTo[Array[String]],
          src("extent").convertTo[Extent]
        )
      case _ => deserializationError("Failed to parse an image band source")
    }
  }
}

