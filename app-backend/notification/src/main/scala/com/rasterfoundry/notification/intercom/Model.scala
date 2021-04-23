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

  @newtype case class ConversationId(underlying: String)
  object ConversationId {
    implicit val encConversationId: Encoder[ConversationId] =
      Encoder.encodeString.contramap(_.underlying)
    implicit val decConversationId: Decoder[ConversationId] =
      Decoder.decodeString.map(ConversationId.apply _)
  }

  @newtype case class Notification(underlying: String)
  @newtype case class Message(underlying: String)
  @newtype case class IntercomToken(underlying: String)

  @newtype case class AdminId(underlying: String)
  object AdminId {
    implicit val encAdminId: Encoder[AdminId] =
      Encoder.encodeString.contramap(_.underlying)
    implicit val decAdminId: Decoder[AdminId] =
      Decoder.decodeString.map(AdminId.apply _)
  }

  case class AdminObject(adminId: AdminId)
  object AdminObject {
    implicit val encAdminObject: Encoder[AdminObject] =
      Encoder.forProduct2("type", "id")(adminObject =>
        ("admin", adminObject.adminId))
  }

  case class UserObject(externalId: ExternalId)
  object UserObject {
    implicit val encUserObject: Encoder[UserObject] = Encoder.forProduct2(
      "type",
      "user_id"
    )(userObject => ("user", userObject.externalId))
  }

  case class ConversationCreate(userId: ExternalId, message: Message)
  object ConversationCreate {
    implicit val encConversationCreate: Encoder[ConversationCreate] =
      Encoder.forProduct2(
        "from",
        "body"
      )(
        conversationCreate =>
          (
            UserObject(conversationCreate.userId),
            conversationCreate.message.underlying
        ))
  }

  case class Conversation(conversationId: ConversationId)
  object Conversation {
    implicit val decConversation: Decoder[Conversation] =
      Decoder.forProduct1("conversation_id")((conversationId: String) =>
        Conversation(ConversationId(conversationId)))
  }

  case class ConversationReply(adminId: AdminId, message: Message)
  object ConversationReply {
    implicit val encConversationReply: Encoder[ConversationReply] =
      Encoder.forProduct4(
        "message_type",
        "type",
        "admin_id",
        "body"
      )(
        conversationReply =>
          (
            "comment",
            "admin",
            conversationReply.adminId.underlying,
            conversationReply.message.underlying
        ))
  }
}
