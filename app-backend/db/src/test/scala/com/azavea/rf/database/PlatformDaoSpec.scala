package com.azavea.rf.database

import com.azavea.rf.datamodel.Platform
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

class PlatformDaoSpec extends FunSuite with Matchers with IOChecker with DBTestConfig {

    def createPlatform: ConnectionIO[Platform] = {
        val platformName = "test platform"
        for {
            usr <- defaultUserQ
            create <- PlatformDao.create(Platform.Create(platformName, usr).toPlatform)
        } yield create
    }

    test("read types") { check(PlatformDao.selectF.query[Platform]) }

    test("insertion") {
        val transaction = for {
            platformIn <- createPlatform
            platformOut <- PlatformDao.query.filter(platformIn.id).selectQ.unique
        } yield platformOut

        val result = transaction.transact(xa).unsafeRunSync
        result.name shouldBe "test platform"
    }

    test("insertion types") {
        createPlatform.map(p => check(PlatformDao.createF(p).query[Platform]))
    }

    test("update") {
        val transaction = for {
            usr <- defaultUserQ
            platformIn <- createPlatform
            platformUpdate <- PlatformDao.update(platformIn.copy(name = "test platform update"), platformIn.id, usr)
            platformOut <- PlatformDao.query.filter(platformIn.id).selectQ.unique
        } yield platformOut

        val result = transaction.transact(xa).unsafeRunSync
        result.name shouldBe "test platform update"
    }

    test("delete") {
        val transaction = for {
            platformIn <- createPlatform
            platformDelete <- PlatformDao.query.filter(platformIn.id).delete
        } yield platformDelete

        transaction.transact(xa).unsafeRunSync shouldBe 1
    }
}
