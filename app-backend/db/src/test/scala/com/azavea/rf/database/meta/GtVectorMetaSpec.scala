package com.azavea.rf.database.meta

import com.azavea.rf.datamodel.AOI
import com.azavea.rf.database._
import com.azavea.rf.database.Implicits._

import doobie._, doobie.implicits._
import cats._, cats.data._, cats.effect.IO
import cats.syntax.either._
import doobie.postgres._, doobie.postgres.implicits._
import doobie.scalatest.imports._
import org.scalatest._
import geotrellis.vector._


class GtVectorMetaSpec extends FunSpec with Matchers with DBTestConfig {

  case class GeometryClass(
    id: Int,
    point: Projected[Point],
    line: Projected[Line],
    poly: Projected[Polygon],
    multipoly: Projected[MultiPolygon]
  )

  val drop: Update0 =
  sql"""
    DROP TABLE IF EXISTS geom_test_table
  """.update

  val createTable = sql"""
    CREATE TABLE jtsgeom_test_table (
      id          integer                      NOT NULL UNIQUE,
      point       geometry(Point, 3857)        NOT NULL,
      line        geometry(LineString, 3857)   NOT NULL,
      poly        geometry(Polygon, 3857)      NOT NULL,
      multipoly   geometry(MultiPolygon, 3857)      NOT NULL
    )
  """.update

  def insert(geomClass: GeometryClass) = sql"""
    INSERT INTO jtsgeom_test_table (id, point, line, poly, multipoly)
    VALUES (${geomClass.id}, ${geomClass.point}, ${geomClass.line}, ${geomClass.poly}, ${geomClass.multipoly})
  """.update

  def select(id: Int) = sql"""
    SELECT id, point, line, poly, multipoly FROM jtsgeom_test_table WHERE id = $id
  """.query[GeometryClass].unique

  it("should be able to go in and then come back out") {
    //val point = new Point(1, 2)
    val point = Projected(Point(1, 2), 3857)
    val line = Projected(Line(Point(0, 1), Point(123, 412), Point(51, 12)), 3857)
    val poly = Projected(
      Polygon(Array(Point(0, 0), Point(0, 1), Point(1, 1), Point(0, 0))),
      3857
    )
    val mpoly = Projected(
      MultiPolygon(Array(
        Polygon(Array(Point(0, 0), Point(0, 1), Point(1, 1), Point(0, 0))),
        Polygon(Array(Point(1, 0), Point(10, 1), Point(1, 1), Point(1, 0))),
        Polygon(Array(Point(10, 0), Point(10, 1), Point(100, 1), Point(10, 0)))
      )),
      3857
    )

    val geomOut = for {
      _  <- createTable.run
      _  <- insert(GeometryClass(123, point, line, poly, mpoly)).run
      js <- select(123)
    } yield js

    val results = geomOut.transact(xa).unsafeRunSync
    results.point shouldBe point
    results.line shouldBe line
    results.poly shouldBe poly
    results.multipoly shouldBe mpoly
  }
}
