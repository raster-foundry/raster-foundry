package com.rasterfoundry.database.meta

import doobie._
import doobie.postgres.pgisimplicits._
import doobie.util.invariant.InvalidObjectMapping
import geotrellis.proj4.CRS
import geotrellis.vector._
import geotrellis.vector.io.wkt.WKT
import org.postgis.PGgeometry

import scala.reflect.ClassTag
import scala.reflect.runtime.universe.TypeTag

trait GtWktMeta {

  implicit val pgMeta: Meta[PGgeometry] =
    Meta.Advanced.other[PGgeometry]("geometry")

  // Constructor for geometry types via WKT reading/writing
  @SuppressWarnings(Array("AsInstanceOf"))
  private def geometryType[A >: Null <: Geometry: TypeTag](
      implicit
      A: ClassTag[A]): Meta[Projected[A]] =
    PGgeometryType.timap[Projected[A]](pgGeom => {
      val split = PGgeometry.splitSRID(pgGeom.getValue)
      val srid = split(0).splitAt(5)._2.toInt
      val geom = WKT.read(split(1))
      try Projected[A](A.runtimeClass.cast(geom).asInstanceOf[A], srid)
      catch {
        case _: ClassCastException =>
          throw InvalidObjectMapping(
            A.runtimeClass,
            pgGeom.getGeometry.getClass
          )
      }
    })(geom => {
      val wkt = s"SRID=${geom.srid};" + WKT.write(geom)
      val pgGeom = PGgeometry.geomFromString(wkt)
      new PGgeometry(pgGeom)
    })

  implicit val GeometryType: Meta[Projected[Geometry]] =
    geometryType[Geometry]
  implicit val GeometryCollectionType: Meta[Projected[GeometryCollection]] =
    geometryType[GeometryCollection]
  implicit val MultiLineStringType: Meta[Projected[MultiLineString]] =
    geometryType[MultiLineString]
  implicit val MultiPolygonType: Meta[Projected[MultiPolygon]] =
    geometryType[MultiPolygon]
  implicit val LineStringType: Meta[Projected[LineString]] =
    geometryType[LineString]
  implicit val MultiPointType: Meta[Projected[MultiPoint]] =
    geometryType[MultiPoint]
  implicit val PolygonType: Meta[Projected[Polygon]] =
    geometryType[Polygon]
  implicit val PointType: Meta[Projected[Point]] =
    geometryType[Point]
  implicit val ComposedGeomType: Meta[Projected[GeometryCollection]] =
    geometryType[GeometryCollection]

  // because projected geoms and ProjectedExtents use different indications
  // of projection info, we have to pretend that the mapping is perfect.
  // fortunately, we know that everything in the database is stored in a sensible
  // projection that definitely has an SRID, so this Should be Fine :tm:
  // (we also _shouldn't_ need the extent => poly conversion)
  @SuppressWarnings(Array("OptionGet"))
  implicit val extentMeta: Meta[ProjectedExtent] =
    geometryType[Polygon].imap(projGeom =>
      ProjectedExtent(projGeom.geom.extent, CRS.fromEpsgCode(projGeom.srid)))(
      projExtent =>
        Projected(projExtent.extent.toPolygon, projExtent.crs.epsgCode.get))

}
