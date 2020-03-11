package com.rasterfoundry.batch.groundwork

import io.circe.{Decoder, Encoder}
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
  object UserId {
    implicit val encUserId: Encoder[UserId] =
      Encoder.encodeString.contramap(_.underlying)
    implicit val decUserId: Decoder[UserId] =
      Decoder.decodeString.map(UserId.apply _)
  }
  @newtype case class Message(underlying: String)
  @newtype case class IntercomToken(underlying: String)

  @ConfiguredJsonCodec case class FromObject(adminId: UserId, _type: String)
  @ConfiguredJsonCodec case class ToObject(
      externalId: ExternalId,
      _type: String
  )

  case class MessagePost(adminId: UserId, userId: ExternalId, msg: Message)
  object MessagePost {
    implicit val encMessagePost: Encoder[MessagePost] = Encoder.forProduct4(
      "from",
      "to",
      "body",
      "message_type"
    )(
      messagePost =>
      (
        FromObject(messagePost.adminId, "admin"),
        ToObject(messagePost.userId, "user"),
        messagePost.msg.underlying,
        "inapp"
      )
    )
  }

}
