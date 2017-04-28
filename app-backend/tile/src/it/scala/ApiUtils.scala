package com.azavea.rf.tile

import io.circe._
import io.circe.parser._
import scalaj.http._
import cats.syntax.either._
import geotrellis.vector._
import geotrellis.slick._

import scala.util._
import java.util.UUID

object ApiUtils {

  /** Use the RF API to Retrieve (and cache due to laziness) the JWT authorization
    *  using a refresh token kept in either application.conf or in the environment
    *  variable 'REFRESH_TOKEN'.
    *
    * @note It would've been nice to lean on gatling's 'saveAs' functionality to provide
    *        the response from the 'refresh_token' endpoint, but multiple calls to said
    *        endpoint yield 501s for a short time after the initial POST. This causes things
    *        to fail spectacularly. By retrieving the token manually a single time, we avoid
    *        this behavior.
    */
  lazy val getAuthToken = {
    val res = Http(Config.RF.tokenEndpoint)
      .method("POST")
      .header("Content-Type", "application/json")
      .postData(s"""{ "refresh_token": "${Config.RF.refreshToken}" }""")
      .asString

    val parsed = parse(res.body).getOrElse(Json.Null)

    parsed.hcursor.get[String]("id_token") match {
      case Right(token) => token
      case Left(x) => throw new IllegalStateException(s"Parsing auth token failed in ${res}")
    }
  }


  /** Use the RF API to get a project's Bounding Box
    *
    * @note We're going to swallow parsing exceptions inside the [[None]] case due to
    *        the possibly non-existent [[Extent]] on our targetted project
    *
    * @note The targetted project *must* be available to the user whose refresh token
    *        (see [[getAuthToken]] above)
    */
  def getProjectBBox(projId: UUID) =
    Try {
      val res = Http(Config.RF.projectEndpoint + projId.toString)
        .method("GET")
        .header("authorization", s"Bearer ${getAuthToken}")
        .asString

      var parsed = parse(res.body).getOrElse(Json.Null)

      /** Unfortunately, this [[Extent]] is encoded as a [[Projected[Geometry]]],
        *  which means we'll need a custom decoder instance.
        */
      implicit val decodeExtent: Decoder[Extent] = new Decoder[Extent] {
        final def apply(c: HCursor): Decoder.Result[Extent] =
          Either.catchNonFatal {
            val coords = c.downField("coordinates")
            val minCurs = coords.downArray.values.get(0).hcursor
            val maxCurs = coords.downArray.values.get(2).hcursor

            val xmin = minCurs.values.get(0).as[Double].right.get
            val ymin = minCurs.values.get(1).as[Double].right.get
            val xmax = maxCurs.values.get(0).as[Double].right.get
            val ymax = maxCurs.values.get(1).as[Double].right.get

            Extent(xmin, ymin, xmax, ymax)
          }.leftMap({ t =>
            DecodingFailure("Parsing failure", c.history)
          })
      }

      parsed.hcursor.get[Extent]("extent") match {
        case Right(bbox) =>
          Some(bbox)
        case Left(x) =>
          None
      }
    }.toOption.flatten

}
