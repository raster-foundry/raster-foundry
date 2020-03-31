package com.rasterfoundry.notification.intercom

import io.circe.{Decoder, Encoder}
import io.estatico.newtype.macros.newtype

@SuppressWarnings(Array("AsInstanceOf"))
object Model {

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

  case class FromObject(adminId: UserId)
  object FromObject {
    implicit val encFromObject: Encoder[FromObject] =
      Encoder.forProduct2("type", "id")(
        fromObject => ("admin", fromObject.adminId)
      )
  }

  case class ToObject(externalId: ExternalId)
  object ToObject {
    implicit val encToObject: Encoder[ToObject] = Encoder.forProduct2(
      "type",
      "user_id"
    )(toObject => ("user", toObject.externalId))
  }

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
          FromObject(messagePost.adminId),
          ToObject(messagePost.userId),
          messagePost.msg.underlying,
          "inapp"
      )
    )
  }

}
