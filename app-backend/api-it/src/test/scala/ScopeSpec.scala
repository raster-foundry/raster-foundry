package com.rasterfoundry.api.it

import com.rasterfoundry.datamodel._

import cats.implicits._
import io.circe._
import io.circe.generic.semiauto.deriveDecoder
import io.circe.syntax._
import org.scalatest.FunSpec
import org.scalatest.prop.TableDrivenPropertyChecks._

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

  val bogusId = UUID.randomUUID

  // FLOW / TODO
  // - get the csv location from testing resources
  // - read the csv into a `Table` -- http://www.scalatest.org/user_guide/table_driven_property_checks
  // - use table drive property checks to verify that with the scope present,
  //   we don't get a 403
  // - do the same for when the scope is absent

  // this will come from the csv in the real version, but just setting this up for now
  val routes = Table(
    ("Path", "Domain:Action", "Verb"),
    ("/datasources/", "datasources:read", "get"),
    ("/datasources/", "datasources:create", "post"),
    ("/datasources/", "bogus:permission", "fakeVerb")
  )

  forAll(routes) { (path: String, scope: String, verb: String) =>
    CsvRow.fromStringsE(path, scope, verb) map { _ =>
      assert(true)
    } getOrElse { fail(s"Could not decode $path, $scope, and $verb into row types") }
  }
}
