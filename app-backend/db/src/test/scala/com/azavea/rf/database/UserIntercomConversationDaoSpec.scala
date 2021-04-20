package com.rasterfoundry.database

import com.rasterfoundry.common.Generators.Implicits._
import com.rasterfoundry.datamodel._

import doobie.implicits._
import org.scalacheck.Prop.forAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.Checkers

class UserIntercomConversationDaoSpec
    extends AnyFunSuite
    with Matchers
    with Checkers
    with DBTestConfig
    with PropTestHelpers
    with ConnectionIOLogger {
  test("insert a user intercom conversation and then get it") {
    check {
      forAll(
        (
            userCreate: User.Create,
            conversationId: String
        ) => {
          val insertAndGetIO = for {
            user <- UserDao.create(userCreate)
            inserted <- UserIntercomConversationDao.insertUserConversation(
              user.id,
              conversationId
            )
            dbConvo <- UserIntercomConversationDao.getByUserId(user.id)
          } yield (inserted, user, dbConvo)

          val (insertedConversation, insertedUser, dbConversation) =
            insertAndGetIO.transact(xa).unsafeRunSync

          assert(
            insertedConversation.userId == insertedUser.id,
            "Inserted user conversation's user ID is correct"
          )
          assert(
            insertedConversation.conversationId == conversationId,
            "Inserted user conversation's conversation ID is correct"
          )
          assert(
            dbConversation == Some(
              UserIntercomConversation(insertedUser.id, conversationId)
            ),
            "Fetched user conversation object matches what was inserted"
          )
          true
        }
      )
    }
  }
}
