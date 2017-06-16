package com.azavea.rf.common

import scala.collection.mutable
import java.io.{PrintStream, ByteArrayOutputStream}

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.actor.ActorSystem
import akka.stream.Materializer
import spray.json._
import DefaultJsonProtocol._

import com.typesafe.scalalogging.LazyLogging

trait RollbarNotifier extends LazyLogging {

  implicit val system: ActorSystem
  implicit val materializer: Materializer

  val rollbarApiToken = sys.env.get("ROLLBAR_SERVER_TOKEN") match {
    case Some(t) => t
    case _ =>
      throw new RuntimeException("Rollbar API token must be present to notify rollbar")
  }

  val url = "https://api.rollbar.com/api/1/item/"

  val environment = sys.env.get("ENVIRONMENT") match {
    case Some(env) => env
    case _ => "staging"
  }

  def buildRollbarPayload(e: Throwable): JsObject = JsObject(
    "access_token" -> this.rollbarApiToken.toJson,
    "data" -> JsObject(
      "environment" -> this.environment.toJson,
      "body" -> JsObject(
        "trace_chain" -> {
          val traces = mutable.ListBuffer.empty[JsObject]
          var thr = e
          do {
            traces += createTrace(thr)
            thr = thr.getCause
          } while (thr != null)

          JsArray(traces.toVector)
        }
      )
    )
  )

  def buildRollbarPayload(s: String): JsObject = JsObject(
    "access_token" -> this.rollbarApiToken.toJson,
    "data" -> JsObject(
      "environment" -> this.environment.toJson,
      "body" -> JsObject(
        "message" -> JsObject("body" -> JsString(s))
      )
    )
  )

  def createTrace(throwable: Throwable): JsObject = {
    val frames = throwable.getStackTrace.map { element =>
      val lineNo = if(element.getLineNumber > 0) Seq("lineno" -> element.getLineNumber.toJson) else Nil
      val frame = Seq(
          "class_name" -> element.getClassName.toJson,
          "filename" -> element.getFileName.toJson,
          "method" -> element.getMethodName.toJson
      ) ++ lineNo
      JsObject(Map(frame:_*))
    }

    val raw = try {
      val baos = new ByteArrayOutputStream()
      val ps = new PrintStream(baos)
      throwable.printStackTrace(ps)
      ps.close()
      baos.close()
      Some(baos.toString("UTF-8"))
    } catch {
      case e: Exception => {
        logger.debug("Exception printing stack trace.", e)
        None
      }
    }

    JsObject(
      "frames" -> JsArray(frames.toVector),
      "exception" -> JsObject(
          "class" -> throwable.getClass.getCanonicalName.toJson,
          "message" -> throwable.getMessage.toJson),
      "raw" -> raw.toJson
    )
  }

  def sendError(e: Throwable): Unit = {
    val payload = this.buildRollbarPayload(e)
    sendError(payload)
  }

  def sendError(s: String): Unit = {
    val payload = this.buildRollbarPayload(s)
    sendError(payload)
  }

  def sendError(payload: JsObject): Unit = {
    if (this.environment != "development") {
      Http().singleRequest(
        HttpRequest(HttpMethod("POST", false, false, RequestEntityAcceptance.Expected),
          uri = this.url,
          entity = HttpEntity(ContentTypes.`application/json`,
            payload.toString))
      )
    }
  }
}
