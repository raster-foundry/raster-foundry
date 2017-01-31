package com.azavea.rf.common

import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Success, Failure}

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.actor.ActorSystem
import akka.stream.Materializer
import spray.json._
import DefaultJsonProtocol._

trait RollbarNotifier {

  implicit val system: ActorSystem
  implicit val materializer: Materializer

  val rollbarApiToken = sys.env.get("ROLLBAR_API_TOKEN") match {
    case Some(t) => t
    case _ =>
      throw new RuntimeException("Rollbar API token must be present to notify rollbar")
  }

  val url = "https://api.rollbar.com/api/1/item/"

  val environment = sys.env.get("ENVIRONMENT") match {
    case Some(env) => env
    case _ => "staging"
  }

  def buildRollbarPayload(e: Exception): JsObject = JsObject(
    "access_token" -> this.rollbarApiToken.toJson,
    "data" -> JsObject(
      "environment" -> this.environment.toJson,
      "body" -> JsObject(
        "message" -> JsObject(
          "body" -> e.getMessage.toJson,
          "stackTrace" -> (e.getStackTrace map { x: StackTraceElement => x.toString })
            .mkString("\n")
            .toJson
        )
      )
    )
  )

  def sendError(e: Exception): Unit = {
    val payload = this.buildRollbarPayload(e)
    Http().singleRequest(
      HttpRequest(HttpMethod("POST", false, false, RequestEntityAcceptance.Expected),
                  uri = this.url,
                  entity = HttpEntity(ContentTypes.`application/json`,
                                      payload.toString))
    )
  }
}
