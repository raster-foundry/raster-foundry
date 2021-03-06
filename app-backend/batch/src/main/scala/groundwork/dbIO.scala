package com.rasterfoundry.batch.groundwork

import com.rasterfoundry.database.UserIntercomConversationDao
import com.rasterfoundry.datamodel.UserIntercomConversation
import com.rasterfoundry.notification.intercom.GroundworkConfig

import cats.effect.{ContextShift, IO}
import doobie.Transactor
import doobie.implicits._

class DbIO(
    xa: Transactor[IO]
)(implicit cs: ContextShift[IO]) {
  val groundworkConfig =
    GroundworkConfig(Config.intercomToken, Config.intercomAdminId)

  def getConversation(
      id: String
  ): IO[Option[UserIntercomConversation]] =
    UserIntercomConversationDao.getByUserId(id).transact(xa)

  def insertConversation(
      userId: String,
      conversationId: String
  ): IO[UserIntercomConversation] =
    UserIntercomConversationDao
      .insertUserConversation(userId, conversationId)
      .transact(xa)
}
