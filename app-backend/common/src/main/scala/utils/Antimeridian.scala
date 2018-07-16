package com.azavea.rf.common.utils

import geotrellis.proj4._
import geotrellis.slick.Projected
import geotrellis.vector._

object AntimeridianUtils {
  def crossesAntimeridian(multi: MultiPolygon) = {
    val latLngFootprint = multi.reproject(WebMercator, LatLng)
    val antiMeridian = Projected(Line(Point(180, -90), Point(180, 90)), 4326)
    val longitudeShifted = latLngFootprint.vertices.map {
      case p: Point if p.y < 0 =>
        Point(p.x, p.y + 360)
      case p: Point if p.y >= 0 =>
        Point(p.x, p.y)
    }
    longitudeShifted.intersects(antiMeridian)
  }

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

  def splitDataFootprintOverAntimeridian(multi: MultiPolygon, code: Int): Projected[MultiPolygon] = {
    val latLngFootprint = multi.reproject(WebMercator, LatLng)
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
        leftPoly.union(rightPoly).asMultiPolygon.get
      case (Some(leftPoly: MultiPolygon), _) =>
        leftPoly
      case (_, Some(rightPoly: MultiPolygon)) =>
        rightPoly
      case (_, _) =>
        throw new RuntimeException(
          "Error while splitting polygon over anti-meridian: neither side of the anti-meridian had a polygon")
    }
    unioned.reproject(LatLng, CRS.fromName(s"EPSG:$code"))
    Projected(unioned, code)
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
      case (true, Some(footprint), Some(code)) =>
        Some(AntimeridianUtils.splitDataFootprintOverAntimeridian(footprint, code))
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
