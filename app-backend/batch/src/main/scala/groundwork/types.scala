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
  @newtype case class ExternalId(underlying: String)
  object ExternalId {
    implicit val encExternalId: Encoder[ExternalId] =
      Encoder.encodeString.contramap(_.underlying)
    implicit val decExternalId: Decoder[ExternalId] =
      Decoder.decodeString.map(ExternalId.apply _)
  }

  @newtype case class Notification(underlying: String)
  @newtype case class UserId(underlying: String)
  @newtype case class Message(underlying: String)
  @newtype case class IntercomToken(underlying: String)

  case class IntercomSearchQuery(externalId: ExternalId)
  object IntercomSearchQuery {
    implicit val encIntercomSearchQuery: Encoder[IntercomSearchQuery] =
      Encoder.forProduct3(
        "field",
        "operator",
        "value"
      )(query => ("external_id", "=", query.externalId))

    implicit val decIntercomSearchQuery: Decoder[IntercomSearchQuery] =
      Decoder.forProduct3(
        "field",
        "operator",
        "value"
      )(
        (_: String, _: String, email: ExternalId) => IntercomSearchQuery(email)
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
