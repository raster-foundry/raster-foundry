package com.azavea.rf.database

import java.sql.Timestamp

import com.azavea.rf.datamodel._
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

class ProjectDaoSpec extends FunSuite with Matchers with IOChecker with DBTestConfig {

  def createProject: ConnectionIO[Project] = {
    val testName = "test project name"

    for {
      usr <- defaultUserQ
      org <- rootOrgQ
      projectIn <- {
        val projectCreate = Project.Create(org.id, testName, "description",
          Visibility.Public, Visibility.Public, true, 123L, Some(usr.id), List("tags"), false, None
        )
        ProjectDao.insertProject(projectCreate, usr)
      }
    } yield {
      println(s"Created Project with ID: ${projectIn.id}")
      projectIn
    }
  }

  test("insertion") {
    val transaction = for {
      projectIn <- createProject
      projectOut <- ProjectDao.query.filter(fr"id = ${projectIn.id}").selectQ.unique
    } yield projectOut

    val result = transaction.transact(xa).unsafeRunSync
    result.name shouldBe "test project name"
  }

  test("insertion types") { check(ProjectDao.selectF.query[Project]) }

  test("update") {
    val transaction = for {
      user <- defaultUserQ
      projectIn <- createProject
      projectUpdate <- {
        val updateQuery = ProjectDao.updateProjectQ(projectIn, projectIn.id, user)
        check(updateQuery)
        updateQuery.run
      }
    } yield projectUpdate

    val result = transaction.transact(xa).unsafeRunSync
    result shouldBe 1
  }

  test("delete") {
    val transaction = for {
      user <- defaultUserQ
      projectIn <- createProject
      projectDelete <- ProjectDao.deleteProject(projectIn.id, user)
    } yield projectDelete

    val result = transaction.transact(xa).unsafeRunSync
    result shouldBe 1
  }

}

