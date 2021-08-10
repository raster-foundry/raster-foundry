package com.rasterfoundry.api.it

import com.rasterfoundry.datamodel._

import cats.effect._
import cats.implicits._
import com.github.tototoshi.csv._
import com.typesafe.config.ConfigFactory
import io.circe._
import io.circe.generic.semiauto.deriveDecoder
import io.circe.syntax._
import org.http4s.Method._
import org.http4s._
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.client._
import org.http4s.client.blaze._
import org.http4s.client.dsl.io._
import org.scalatest.compatible.Assertion
import org.scalatest.funspec.AnyFunSpec

import scala.concurrent.ExecutionContext.global
import scala.io.Source

import java.net.URI
import java.util.UUID
import java.util.concurrent._

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

final case class UnparsedRow(path: String, scope: String, verb: String) {
  def tupled = (path, scope, verb)
}

final case class ParsedCsvRow(path: String, scope: Scope, verb: Verb)
object ParsedCsvRow {
  def fromStringsE(
      pathS: String,
      scopeS: String,
      verbS: String
  ): Either[DecodingFailure, ParsedCsvRow] = {
    // guess whether the compiler can find the implicits if I don't explicitly cast
    // Right(pathS) to Decoder.Result[String]
    // (and no, before you ask, Either.right[DecodingFailure, String] doesn't help)
    (
      Right(pathS): Decoder.Result[String],
      Decoder[Scope].decodeJson(scopeS.asJson),
      Verb.fromStringE(verbS): Decoder.Result[Verb]
    ).mapN {
      case (p, sc, v) => ParsedCsvRow(p, sc, v)
    }
  }
}

final case class SimResponse(simResult: Boolean)

object SimResponse {
  implicit val decSimResponse: Decoder[SimResponse] = deriveDecoder
}

final case class TokenResponse(id_token: String)

object TokenResponse {
  implicit val decTokenResponse: Decoder[TokenResponse] = deriveDecoder
}

class ScopeSpec extends AnyFunSpec {

  val blockingPool = Executors.newCachedThreadPool()
  val blocker = Blocker.liftExecutorService(blockingPool)
  implicit val contextShift: ContextShift[IO] =
    IO.contextShift(blocker.blockingContext)
  val httpClient: Resource[IO, Client[IO]] =
    BlazeClientBuilder[IO](blocker.blockingContext).resource

  private val config = ConfigFactory.load()

  val apiHost = config.getString("apiHost")
  val csvPath = config.getString("scopeITCSVLocation")
  val refreshToken = config.getString("refreshToken")

  val bogusId = UUID.randomUUID

  // match strings of alpha characters between braces
  val idSegment = "\\{[aA-zZ]*\\}".r
  val tokenQueryParameterSegment = "\\{token\\}".r

  def subUUID(path: String): String = idSegment.replaceAllIn(path, s"$bogusId")

  def makeRoute(path: String): Uri = {
    val cleaned = subUUID(path)
    Uri.unsafeFromString(s"$apiHost$cleaned")
  }

  def authTokenIO(client: Client[IO]): IO[TokenResponse] = {
    val tokenRoute = makeRoute("/api/tokens/")
    val request =
      POST(
        Map("refresh_token" -> refreshToken).asJson,
        tokenRoute
      )
    client.expect[TokenResponse](request)
  }

  def memoizedAuthTokenIO(client: Client[IO]) =
    Async.memoize[IO, TokenResponse] {
      authTokenIO(client)
    } flatMap { identity }

  def getSimResult(
      row: ParsedCsvRow,
      expectation: Boolean,
      tokenResponse: TokenResponse,
      client: Client[IO]
  ): IO[Assertion] = {
    val requestUri = makeRoute(row.path)
    val policySimHeader = Header("X-PolicySim", "true")
    val authHeader =
      Header("Authorization", s"Bearer ${tokenResponse.id_token}")
    val scopeModHeader = if (expectation) {
      Header("X-PolicySim-Include", row.scope.asJson.noSpaces.replace("\"", ""))
    } else {
      Header(
        "X-PolicySim-Exclude",
        row.scope.asJson.noSpaces.replace("\"", "")
      )
    }
    val headers = List(policySimHeader, authHeader, scopeModHeader)
    val response = client.expect[SimResponse](row.verb match {
      case Post =>
        POST(requestUri, headers: _*)
      case Get =>
        GET(requestUri, headers: _*)
      case Delete =>
        DELETE(requestUri, headers: _*)
      case Put =>
        PUT(requestUri, headers: _*)
      case Patch =>
        PATCH(requestUri, headers: _*)
    })
    // for some reason I'm not allowed to bail on the Id wrapper in the previous step, though I'd really
    // prefer to. this is a bit janky but I'm not sure what to do about it.
    response map { resultBody =>
      assert(
        resultBody == SimResponse(expectation),
        s"""
        |
        | Authorization expectation failed.
        | Received $resultBody
        | Expected $expectation
        | From url: $requestUri
        | """.trim.stripMargin
      )
    }

  }

  def expectAllowed(
      row: ParsedCsvRow,
      tokenResponse: TokenResponse,
      client: Client[IO]
  ): IO[Assertion] = getSimResult(row, true, tokenResponse, client)

  def expectForbidden(
      row: ParsedCsvRow,
      tokenResponse: TokenResponse,
      client: Client[IO]
  ): IO[Assertion] = getSimResult(row, false, tokenResponse, client)

  val unparsedRows = CSVReader.open(csvPath).all().drop(1) map {
    case p :: s :: v :: Nil => Right(UnparsedRow(p, s, v))
    case r =>
      Left(s"$r does not have exactly 3 entries and will not be checked.")
  }

  def inputDataFailureMessage(path: String, scope: String, verb: String) = s"""
    | Problem in input data -- could not decode $path, $scope, and $verb
    | into row types or base request construction failed""".trim.stripMargin

  describe("Policy simulation") {
    it("reports expected failure when relevant scopes are excluded") {
      httpClient
        .use({ client =>
          unparsedRows traverse {
            case Left(err) =>
              IO { fail(s"Couldn't parse a row appropriately: $err") }
            case Right(UnparsedRow(path, scope, verb)) => {
              (for {
                tokenResponse <- memoizedAuthTokenIO(client)
                updatedPath = tokenQueryParameterSegment.replaceAllIn(
                  path,
                  s"${tokenResponse.id_token}"
                )
                row <- IO.fromEither(
                  ParsedCsvRow.fromStringsE(updatedPath, scope, verb)
                )
                result <- expectForbidden(row, tokenResponse, client)
              } yield { result })
            }
          }

        })
        .unsafeRunSync

    }

    it("reports expected success when relevant scopes are included") {
      httpClient
        .use({ client =>
          unparsedRows traverse {
            case Left(err) =>
              IO { fail(s"Couldn't parse a row appropriately: $err") }
            case Right(UnparsedRow(path, scope, verb)) => {
              (for {
                tokenResponse <- memoizedAuthTokenIO(client)
                updatedPath = tokenQueryParameterSegment.replaceAllIn(
                  path,
                  s"${tokenResponse.id_token}"
                )
                row <- IO.fromEither(
                  ParsedCsvRow.fromStringsE(updatedPath, scope, verb)
                )
                result <- expectAllowed(row, tokenResponse, client)
              } yield {
                result
              })
            }

          }
        })
        .unsafeRunSync

    }
  }
}
