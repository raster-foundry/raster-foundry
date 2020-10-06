package com.rasterfoundry.database.meta

import com.rasterfoundry.database._

import doobie._, doobie.implicits._
import doobie.postgres.circe.jsonb.implicits._
import io.circe._
import io.circe.syntax._

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class CirceJsonbMetaSpec extends AnyFunSpec with Matchers with DBTestConfig {

  case class JsonClass(id: Int, json: Json)

  val drop: Update0 =
    sql"""
    DROP TABLE IF EXISTS jsonb_test_table
  """.update

  val createTable = sql"""
    CREATE TABLE IF NOT EXISTS jsonb_test_table (
      id          integer       NOT NULL UNIQUE,
      json        jsonb         NOT NULL
    )
  """.update

  def insert(jsonClass: JsonClass) = sql"""
    INSERT INTO jsonb_test_table (id, json)
    VALUES (${jsonClass.id}, ${jsonClass.json})
  """.update

  def select(id: Int) = sql"""
    SELECT json FROM jsonb_test_table WHERE id = $id
  """.query[Json].unique

  it("should be able to go in and then come back out") {
    val jsonIn = List(123, 234, 345).asJson

    val jsonOut = for {
      _ <- drop.run
      _ <- createTable.run
      _ <- insert(JsonClass(123, jsonIn)).run
      js <- select(123)
    } yield js

    jsonOut.transact(xa).unsafeRunSync shouldBe jsonIn
  }
}
