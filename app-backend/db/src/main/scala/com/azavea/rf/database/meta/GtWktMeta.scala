package com.azavea.rf.database.meta

import doobie._, doobie.implicits._
import cats._, cats.data._, cats.effect.IO
import cats.syntax.either._
import doobie.postgres._, doobie.postgres.implicits._, doobie.postgres.pgisimplicits._
import doobie.util.invariant.InvalidObjectMapping
import geotrellis.vector.io.wkt.WKT
import geotrellis.vector._
import geotrellis.slick.Projected
import org.postgis.PGgeometry

import scala.reflect.runtime.universe.TypeTag
import scala.reflect.ClassTag


trait GtWktMeta {

  implicit val pgMeta = Meta.other[PGgeometry]("geometry")

  // Constructor for geometry types via WKT reading/writing
  private def geometryType[A >: Null <: Geometry: TypeTag](implicit A: ClassTag[A]): Meta[Projected[A]] =
    PGgeometryType.xmap[Projected[A]](
      pgGeom => {
        val split = PGgeometry.splitSRID(pgGeom.getValue)
        val srid = split(0).splitAt(5)._2.toInt
        val geom = WKT.read(split(1))
        try Projected[A](
          A.runtimeClass.cast(geom).asInstanceOf[A],
          srid
        )
        catch {
          case _: ClassCastException => throw InvalidObjectMapping(A.runtimeClass, pgGeom.getGeometry.getClass)
        }
      },
      geom => {
        val wkt = s"SRID=${geom.srid};" + WKT.write(geom)
        val pgGeom = PGgeometry.geomFromString(wkt)
        new PGgeometry(pgGeom)
      }
    )

  implicit val GeometryType           = geometryType[Geometry]
  implicit val GeometryCollectionType = geometryType[GeometryCollection]
  implicit val MultiLineStringType    = geometryType[MultiLine]
  implicit val MultiPolygonType       = geometryType[MultiPolygon]
  implicit val LineStringType         = geometryType[Line]
  implicit val MultiPointType         = geometryType[MultiPoint]
  implicit val PolygonType            = geometryType[Polygon]
  implicit val PointType              = geometryType[Point]
  implicit val ComposedGeomType       = geometryType[GeometryCollection]

}

