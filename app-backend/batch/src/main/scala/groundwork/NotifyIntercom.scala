package com.rasterfoundry.batch.groundwork

import io.circe.{Decoder, Encoder}
import io.circe.generic.JsonCodec
import io.estatico.newtype.macros.newtype
import io.circe.generic.extras.{Configuration, ConfiguredJsonCodec}

object types {
  implicit val jsonConfig: Configuration =
    Configuration.default.withSnakeCaseMemberNames.copy(transformMemberNames = {
      case "_type" => "type"
      case other   => other
    })
  @newtype case class EmailAddress(underlying: String)
  object EmailAddress {
    implicit val encEmailAddress: Encoder[EmailAddress] =
      Encoder.encodeString.contramap(_.underlying)
    implicit val decEmailAddress: Decoder[EmailAddress] =
      Decoder.decodeString.map(EmailAddress.apply _)
  }

  @newtype case class Notification(underlying: String)
  @newtype case class UserId(underlying: String)
  @newtype case class Message(underlying: String)
  @newtype case class IntercomToken(underlying: String)

  case class IntercomSearchQuery(email: EmailAddress)
  object IntercomSearchQuery {
    implicit val encIntercomSearchQuery: Encoder[IntercomSearchQuery] =
      Encoder.forProduct3(
        "field",
        "operator",
        "value"
      )(query => ("email", "=", query.email))

    implicit val decIntercomSearchQuery: Decoder[IntercomSearchQuery] =
      Decoder.forProduct3(
        "field",
        "operator",
        "value"
      )(
        (_: String, _: String, email: EmailAddress) =>
          IntercomSearchQuery(email)
      )
  }

  @JsonCodec case class IntercomUser(id: String)
  @JsonCodec case class IntercomPost(query: IntercomSearchQuery)

  @ConfiguredJsonCodec case class Pages(
      _type: String,
      page: Int,
      perPage: Int,
      totalPages: Int
  )

  @ConfiguredJsonCodec case class IntercomSearchResponse(
      _type: String,
      data: List[IntercomUser],
      totalCount: Int,
      pages: Pages
  )

}

trait IntercomNotifier[F[_]] {
  import types._

  def notifyUser(userId: UserId, msg: Message): F[Unit]
  def usersbyEmail(
      intercomToken: IntercomToken,
      email: EmailAddress
  ): F[IntercomSearchResponse]
}

class LiveIntercomNotifier[F[_]] extends IntercomNotifier[F] {
  import types._
  def notifyUser(userId: UserId, msg: Message): F[Unit] = ???
  def usersbyEmail(
      intercomToken: IntercomToken,
      email: EmailAddress
  ): F[IntercomSearchResponse] = ???
}
