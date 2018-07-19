package com.azavea.rf.common.utils

import geotrellis.proj4._
import geotrellis.slick.Projected
import geotrellis.vector._
import geotrellis.vector.io._

object AntimeridianUtils {
  def shiftPoint(p: Point, split: Double, compareMultiplier: Double, inclusive: Boolean, degrees: Double): Point = {
    inclusive match {
      case true => p match {
        case p: Point if p.x * compareMultiplier <= split * compareMultiplier =>
          Point(p.x + degrees, p.y)
        case p: Point if p.x * compareMultiplier > split * compareMultiplier =>
          Point(p.x, p.y)
      }
      case false => p match {
        case p: Point if p.x * compareMultiplier < split * compareMultiplier =>
          Point(p.x + degrees, p.y)
        case p: Point if p.x * compareMultiplier >= split * compareMultiplier =>
          Point(p.x, p.y)
      }
    }
  }

  def crossesAntimeridian(multi: MultiPolygon) = {
    val latLngFootprint = multi.reproject(WebMercator, LatLng)
    val antiMeridian = Projected(Line(Point(180, -90), Point(180, 90)), 4326)
    val longitudeShifted = MultiPolygon(
      latLngFootprint.polygons.map(
        poly => Polygon(poly.vertices.map(shiftPoint(_, 0, 1, false, 360)))
      ))
    longitudeShifted.intersects(antiMeridian)
  }

  def splitDataFootprintOverAntimeridian(multi: MultiPolygon, inputCRS: CRS, outputCRS: CRS): Projected[MultiPolygon] = {
    val latLngFootprint = multi.reproject(inputCRS, LatLng)
    val antiMeridian = Projected(Line(Point(180, -90), Point(180, 90)), 4326)
    val longitudeShifted = MultiPolygon(
      latLngFootprint.polygons.map(
        poly => Polygon(poly.vertices.map(shiftPoint(_, 0, 1, false, 360)))
      ))
    val leftAntiMeridianMask = Polygon(
      Array(Point(0, -90), Point(0, 90), Point(180, 90), Point(180, -90), Point(0, -90)))
    val leftOfAntimeridian = longitudeShifted.intersection(leftAntiMeridianMask).asMultiPolygon
    val rightOfAntimeridian = longitudeShifted.difference(leftAntiMeridianMask).asMultiPolygon.map((multi) =>
      MultiPolygon(multi.polygons.map((poly) => Polygon(poly.vertices.map(shiftPoint(_, 180, -1, true, -360)))))
    );
    val unioned = (leftOfAntimeridian, rightOfAntimeridian) match {
      case (Some(leftPoly: MultiPolygon), Some(rightPoly: MultiPolygon)) =>
        leftPoly.union(rightPoly).asMultiPolygon
      case (Some(leftPoly: MultiPolygon), _) =>
        Some(leftPoly)
      case (_, Some(rightPoly: MultiPolygon)) =>
        Some(rightPoly)
      case (_, _) =>
        throw new RuntimeException(
          "Error while splitting polygon over anti-meridian: neither side of the anti-meridian had a polygon")
    }
    val reprojected = unioned map {
      _.reproject(LatLng, outputCRS)
    }
    (reprojected, outputCRS.epsgCode) match {
      case (Some(multi), Some(code)) =>
      Projected(multi, code)
      case _ => throw new RuntimeException(s"Error while splitting data footprint over antimeridian: ${latLngFootprint.toGeoJson()}")
    }
  }

  def cloneTileFootprint(multi: MultiPolygon, code: Int): Projected[MultiPolygon] = {

    val latLngFootprint = multi.reproject(WebMercator, LatLng)
    val antiMeridian = Projected(Line(Point(180, -90), Point(180, 90)), 4326)
    val longitudeShifted = MultiPolygon(
      latLngFootprint.polygons.map(
        poly => Polygon(poly.vertices.map(shiftPoint(_, 0, 1, false, 360)))
      ))
    val leftClone = MultiPolygon(
      longitudeShifted.polygons.map(
        poly => Polygon(poly.vertices.map(p => Point(p.x - 360, p.y)))
      )
    )

    leftClone.union(longitudeShifted).asMultiPolygon.map(Projected(_, code)) match {
      case Some(m : Projected[MultiPolygon]) => m
      case _ => throw new RuntimeException(s"Error cloning tile footprint across anti-meridian: ${multi}")
    }
  }

  def correctDataFootprint(intersects: Boolean, dataFootprint: Option[MultiPolygon], targetCRS: CRS):
      Option[Projected[MultiPolygon]] = {
    val correctedDataFootprint = (intersects, dataFootprint, targetCRS.epsgCode) match {
      case (true, Some(footprint), _) =>
        Some(splitDataFootprintOverAntimeridian(footprint, targetCRS, targetCRS))
      case (false, Some(footprint), Some(code)) =>
        Some(Projected(footprint, code))
      case _ => None
    }
    correctedDataFootprint
  }

  def correctTileFootprint(intersects: Boolean, tileFootprint: Option[MultiPolygon], targetCRS: CRS):
      Option[Projected[MultiPolygon]] = {
    val correctedTileFootprint = (intersects, tileFootprint, targetCRS.epsgCode) match {
      case (true, Some(footprint), Some(code)) => Some(cloneTileFootprint(footprint, code))
      case (false, Some(footprint), Some(code)) => Some(Projected(footprint, code))
      case _ => None
    }
    correctedTileFootprint
  }
}
