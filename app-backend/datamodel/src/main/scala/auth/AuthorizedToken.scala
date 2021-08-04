package com.rasterfoundry.datamodel.auth

import cats.syntax.all._
import io.circe.{Decoder, Encoder, HCursor}
import io.circe.generic.semiauto._

final case class AuthorizedToken(id_token: String,
                                 access_token: String,
                                 expires_in: Int,
                                 token_type: String)

object AuthorizedToken {
  implicit val encAuthorizedToken: Encoder[AuthorizedToken] = deriveEncoder

  implicit val decAuthorizedToken: Decoder[AuthorizedToken] = { (c: HCursor) =>
    (
      c.get[Option[String]]("id_token"),
      c.get[String]("access_token"),
      c.get[Int]("expires_in"),
      c.get[String]("token_type")
    ).mapN {
      case (idTokenO, accessToken, expiresIn, tokenType) =>
        AuthorizedToken(
          idTokenO getOrElse accessToken,
          accessToken,
          expiresIn,
          tokenType
        )
    }
  }
}
