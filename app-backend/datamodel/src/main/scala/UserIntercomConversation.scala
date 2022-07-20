package com.rasterfoundry.datamodel

import io.circe._
import io.circe.generic.semiauto._

final case class UserIntercomConversation(
    userId: String,
    conversationId: String
)

object UserIntercomConversation {
  implicit val encUserIntercomConversation: Encoder[UserIntercomConversation] =
    deriveEncoder
  implicit val decUserIntercomConversation: Decoder[UserIntercomConversation] =
    deriveDecoder
}
