package com.rasterfoundry.api.it

import com.rasterfoundry.datamodel._

import cats.implicits._
import com.softwaremill.sttp.{
  DeserializationError,
  Empty,
  Id,
  Request,
  RequestT,
  Response,
  Uri
}
import com.softwaremill.sttp.circe._
import com.softwaremill.sttp.quick._
import com.typesafe.config.ConfigFactory
import io.circe._
import io.circe.generic.semiauto.deriveDecoder
import io.circe.syntax._
import org.scalatest.FunSpec
import org.scalatest.prop.TableDrivenPropertyChecks._

import java.net.URI
import java.util.UUID

sealed abstract class Verb
case object Get extends Verb
case object Post extends Verb
case object Put extends Verb
case object Patch extends Verb
case object Delete extends Verb

object Verb {
  def fromStringE(s: String): Either[DecodingFailure, Verb] =
    s.toUpperCase match {
      case "GET"    => Right(Get)
      case "POST"   => Right(Post)
      case "PUT"    => Right(Put)
      case "PATCH"  => Right(Patch)
      case "DELETE" => Right(Delete)
      case _        => Left(DecodingFailure(s"$s is not a valid http verb", List()))
    }
}

final case class CsvRow(path: String, scope: Scope, verb: Verb)

object CsvRow {
  def fromStringsE(
      pathS: String,
      scopeS: String,
      verbS: String
  ): Either[DecodingFailure, CsvRow] = {
    // guess whether the compiler can find the implicits if I don't explicitly cast
    // Right(pathS) to Decoder.Result[String]
    // (and no, before you ask, Either.right[DecodingFailure, String] doesn't help)
    (
      Right(pathS): Decoder.Result[String],
      Decoder[Scope].decodeJson(scopeS.asJson),
      Verb.fromStringE(verbS): Decoder.Result[Verb]
    ).mapN {
      case (p, sc, v) => CsvRow(p, sc, v)
    }
  }
}

final case class SimResponse(simResult: Boolean)

object SimResponse {
  implicit val decSimResponse: Decoder[SimResponse] = deriveDecoder
}

class ScopeSpec extends FunSpec {

  private val config = ConfigFactory.load()

  val apiHost = config.getString("apiHost")
  val csvPath = config.getString("scopeITCSVLocation")

  val bogusId = UUID.randomUUID

  // match strings of alpha characters between braces
  val idSegment = "\\{[aA-zZ]*\\}".r

  def subUUID(path: String): String = idSegment.replaceAllIn(path, s"$bogusId")

  def makeRoute(path: String): Uri = {
    val cleaned = subUUID(path)
    println(cleaned)
    val unsafeUri = URI.create(s"$apiHost$cleaned")
    val out = Uri(unsafeUri)
    println(out)
    out
  }

  def getAuthToken(refreshToken: String): String = ":("

  def getBaseRequest(scope: Scope, expectSuccess: Boolean) = {
    val root = sttp.header("X-PolicySim", "true").auth.bearer(getAuthToken(""))
    val scopeStringNoQuotes = scope.asJson.noSpaces.replace("\"", "")
    if (expectSuccess) {
      root.header("X-PolicySim-Include", scopeStringNoQuotes)
    } else {
      root.header("X-PolicySim-Exclude", scopeStringNoQuotes)
    }
  }

  def addMethod(
      request: RequestT[Empty, String, Nothing],
      path: Uri,
      verb: Verb
  ): Request[Either[DeserializationError[Error], SimResponse], Nothing] =
    (verb match {
      case Get    => request.get(path)
      case Post   => request.post(path)
      case Put    => request.put(path)
      case Patch  => request.patch(path)
      case Delete => request.delete(path)
    }).response(asJson[SimResponse])

  // TODO
  // - get the csv location from testing resources
  // - read the csv into a `Table` -- http://www.scalatest.org/user_guide/table_driven_property_checks
  // - get a bearer token from the api

  // this will come from the csv in the real version, but just setting this up for now
  val routes = Table(
    ("Path", "Domain:Action", "Verb"),
    ("/datasources/", "datasources:read", "get"),
    ("/datasources/", "datasources:create", "post"),
    ("/datasources/{datasourceID}", "datasources:read", "get")
  )

  def getSimResult(row: CsvRow, expectation: Boolean) = {
    val base = getBaseRequest(row.scope, expectation)
    val requestUri = makeRoute(row.path)
    val response
        : Id[Response[Either[DeserializationError[Error], SimResponse]]] =
      addMethod(base, requestUri, row.verb).send()
    println(response)
    // for some reason I'm not allowed to bail on the Id wrapper in the previous step, though I'd really
    // prefer to. this is a bit janky but I'm not sure what to do about it.
    val resultBody: Either[String, SimResponse] = response map { resp =>
      resp.body match {
        case Right(Right(simResp)) => Right(simResp)
        case Left(err)             => Left("body deserialization failed")
      }
    }
    assert(
      resultBody == Right(SimResponse(expectation)),
      "Authorization expectation failed"
    )
  }

  def expectAllowed(row: CsvRow) = getSimResult(row, true)

  def expectForbidden(row: CsvRow): Unit = getSimResult(row, false)

  describe("Policy simulation") {
    it("reports expected failure when relevant scopes are excluded") {
      forAll(routes) { (path: String, scope: String, verb: String) =>
        CsvRow.fromStringsE(path, scope, verb) map { expectForbidden _ } getOrElse {
          fail(s"Could not decode $path, $scope, and $verb into row types")
        }
      }
    }

    it("reports expected success when relevant scopes are included") {
      forAll(routes) { (path: String, scope: String, verb: String) =>
        CsvRow.fromStringsE(path, scope, verb) map { expectAllowed _ } getOrElse {
          fail(s"Could not decode $path, $scope, and $verb into row types")
        }
      }
    }
  }
}
