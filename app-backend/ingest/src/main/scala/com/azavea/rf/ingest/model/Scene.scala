package com.azavea.rf.ingest.model

import spray.json._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport

import geotrellis.vector.Extent
import geotrellis.vector.io._

import java.net.URL

/** Each [[JobDefinition]] is made up of arbitrarily many pyramids which will be output somewhere */
case class Scene(
  output: String,
  sources: Array[ImageSource]
) {
  def zipped = (Stream.continually(output) zip Stream.continually(sources).flatten take sources.length)toArray
}

object Scene extends DefaultJsonProtocol {
  implicit val SceneJsonFormat = jsonFormat2(Scene.apply)
}

