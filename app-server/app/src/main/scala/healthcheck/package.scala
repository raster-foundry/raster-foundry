package com.azavea.rf

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport

import spray.json._


/**
  * Json formats for healthcheck case classes
  *
  */
package object healthcheck extends SprayJsonSupport with DefaultJsonProtocol {

  implicit object HealthCheckStatusJsonFormat extends JsonFormat[HealthCheckStatus.Status] {
    def write(obj: HealthCheckStatus.Status): JsValue = JsString(obj.toString)
    def read(json: JsValue): HealthCheckStatus.Status = json match {
      case JsString(str) => HealthCheckStatus.withName(str)
      case _ => throw new DeserializationException("Enum string expected")
    }
  }

  implicit val serviceCheckFormat = jsonFormat2(ServiceCheck)
  implicit val healthCheckFormat = jsonFormat2(HealthCheck)
}
