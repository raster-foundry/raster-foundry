package com.azavea.rf.database.meta

import com.azavea.rf.datamodel.AOI
import com.azavea.rf.database.Implicits._
import com.azavea.rf.database._

import doobie._, doobie.implicits._
import cats._, cats.data._, cats.effect.IO
import cats.syntax.either._
import io.circe._
import io.circe.syntax._
import doobie.postgres._, doobie.postgres.implicits._
import doobie.util.invariant.InvalidObjectMapping
import doobie.scalatest.imports._
import org.scalatest._


class CirceJsonbMetaSpec extends FunSpec with Matchers with DBTestConfig {

  case class JsonClass(id: Int, json: Json)

  val drop: Update0 =
  sql"""
    DROP TABLE IF EXISTS jsonb_test_table
  """.update

  val createTable = sql"""
    CREATE TABLE jsonb_test_table (
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
      _  <- createTable.run
      _  <- insert(JsonClass(123, jsonIn)).run
      js <- select(123)
    } yield js

    jsonOut.transact(xa).unsafeRunSync shouldBe jsonIn
  }
}

