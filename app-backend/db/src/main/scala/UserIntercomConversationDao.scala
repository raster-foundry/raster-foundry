package com.rasterfoundry.database

import com.rasterfoundry.datamodel.UserIntercomConversation

import doobie._
import doobie.implicits._

object UserIntercomConversationDao extends Dao[UserIntercomConversation] {
  val tableName = "user_intercom_conversations"
  override val fieldNames = List(
    "user_id",
    "conversation_id"
  )

  def selectF: Fragment = fr"SELECT " ++ selectFieldsF ++ fr" FROM " ++ tableF

  def getByUserId(
      userId: String
  ): ConnectionIO[Option[UserIntercomConversation]] =
    query.filter(fr"user_id = $userId").selectOption

  def insertUserConversation(
      userId: String,
      conversationId: String
  ): ConnectionIO[UserIntercomConversation] =
    (fr"INSERT INTO" ++ tableF ++ fr"(" ++ insertFieldsF ++ fr")" ++
      fr"VALUES (${userId}, ${conversationId})").update
      .withUniqueGeneratedKeys[UserIntercomConversation](
        fieldNames: _*
      )

}
