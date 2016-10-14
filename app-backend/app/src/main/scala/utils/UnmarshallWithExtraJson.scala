package com.azavea.rf.utils

import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.unmarshalling._
import akka.stream.Materializer
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json.{JsString, JsObject, JsValue, RootJsonFormat, DefaultJsonProtocol}
import DefaultJsonProtocol._
import scala.concurrent.ExecutionContext


/** This Spray Unmarshaller will inject and replace JSON fields into the request body before parsing it into T
  * This allows to inject things like auto-generated id fields and re-use auto-generated JsonFormats for domain classes.
  */
class UnmarshallWithExtraJson[T: RootJsonFormat](replaceFields: (String, JsValue)*) extends FromRequestUnmarshaller[T] {
  def apply(value: HttpRequest)(implicit ec: ExecutionContext, materializer: Materializer) = {
    // get unmarshaller that can transform a request into Spray Json AST
    val jsonUnmarshaller = implicitly[FromRequestUnmarshaller[JsObject]]
    // Map into the Future[JsObject]
    jsonUnmarshaller(value).map { jsRequest =>
      // add and replace fields in parsed object
      val obj = JsObject(jsRequest.fields ++ replaceFields)
      // Parse AST into what it is supposed to be and return it
      implicitly[RootJsonFormat[T]].read(obj)
    }
  }
}