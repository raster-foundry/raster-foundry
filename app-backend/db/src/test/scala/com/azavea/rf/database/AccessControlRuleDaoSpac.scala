package com.azavea.rf.database

import com.azavea.rf.datamodel.{AccessControlRule, ActionType, ObjectType, SubjectType}
import com.azavea.rf.database.Implicits._
import doobie._
import doobie.implicits._
import cats._
import cats.data._
import cats.effect.IO
import cats.syntax.either._
import doobie.postgres._
import doobie.postgres.implicits._
import doobie.scalatest.imports._
import org.scalatest._
import io.circe._
import io.circe.syntax._
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global

class AccessControlRuleDaoSpec extends FunSuite with Matchers with IOChecker with DBTestConfig {

    def createAccessControlRule: ConnectionIO[AccessControlRule] = {
        for {
            platform <- defaultPlatformQ
            usr <- defaultUserQ
            project <- changeDetectionProjQ
            create <- {
                val acr = AccessControlRule.Create(
                    ObjectType.Project,
                    project.id,
                    SubjectType.User,
                    Some(usr.id),
                    ActionType.View,
                    usr
                )
                val acrM = acr.toAccessControlRule
                AccessControlRuleDao.create(acr.toAccessControlRule)
            }
        } yield create
    }

    test("read types") { check(AccessControlRuleDao.selectF.query[AccessControlRule]) }

    test("insertion") {
        val transaction = for {
            acrIn <- createAccessControlRule
            acrOut <- AccessControlRuleDao.getOption(acrIn.id)
        } yield acrOut

        val result = transaction.transact(xa).unsafeRunSync
        result.get.objectType shouldBe ObjectType.Project
        result.get.subjectType shouldBe SubjectType.User
        result.get.actionType shouldBe ActionType.View
    }

    test("deactivation") {
        val transaction = for {
            usr <- defaultUserQ
            acrIn <- createAccessControlRule
            acrDeactivated <- AccessControlRuleDao.deactivate(acrIn.id, usr)
            acrOut <- AccessControlRuleDao.getOption(acrIn.id)
        } yield acrOut

        val result = transaction.transact(xa).unsafeRunSync
        result.get.isActive shouldBe false
    }
}
