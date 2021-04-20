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
  @newtype case class UserId(underlying: String)
  object UserId {
    implicit val encUserId: Encoder[UserId] =
      Encoder.encodeString.contramap(_.underlying)
    implicit val decUserId: Decoder[UserId] =
      Decoder.decodeString.map(UserId.apply _)
  }
  @newtype case class Message(underlying: String)
  @newtype case class IntercomToken(underlying: String)

  case class AdminObject(adminId: UserId)
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
          AdminObject(messagePost.adminId),
          UserObject(messagePost.userId),
          messagePost.msg.underlying,
          "inapp"
      ))
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

  case class ConversationReply(adminId: UserId, message: Message)
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
