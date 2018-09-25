package com.azavea.rf.common.utils

import geotrellis.proj4._
import geotrellis.vector._
import geotrellis.vector.io._

import com.typesafe.scalalogging.LazyLogging

object AntimeridianUtils extends LazyLogging {
  def shiftPoint(p: Point,
                 split: Double,
                 compareMultiplier: Double,
                 inclusive: Boolean,
                 degrees: Double): Point = {
    inclusive match {
      case true =>
        p match {
          case p: Point
              if p.x * compareMultiplier <= split * compareMultiplier =>
            Point(p.x + degrees, p.y)
          case p: Point
              if p.x * compareMultiplier > split * compareMultiplier =>
            Point(p.x, p.y)
        }
      case false =>
        p match {
          case p: Point
              if p.x * compareMultiplier < split * compareMultiplier =>
            Point(p.x + degrees, p.y)
          case p: Point
              if p.x * compareMultiplier >= split * compareMultiplier =>
            Point(p.x, p.y)
        }
    }
  }

  /*
   *  This does a very simple check:
   *  Reproject the polygon to LatLng
   *  Shift points in the polygon which have negative values by adding 360 to them so the projection is now
   *      on a 0 to 360 degrees projection instead of -180 to 180
   *  If the resulting polygon is smaller, then we assume the polygon crosses the antimeridian.
   *  We can make this assumption because Landsat and Sentinel do not record imagery in sections which
   *  span a large enough portion of the globe to break the assumption
   *  We define the anti-meridian as Line(Point(180, -90), Point(180, 90)) because
   *      in LatLng, it's Line(Point(-180, -90), Point(-180, 90)) and we add 360 to it
   *
   *  Eg: A polygon with a left bound of Point(-179, 1) and a right bound of Point(179, 1)
   *      would stretch across the entire map. The shifted bounds of Point(179, 1) and Point(181, 1)
   *      have a smaller area, so we split the polygon and shift the half which is > 180 back 360 degrees
   *
   *      A polygon with bounds Point(-1, 1) and Point(1, 1) when shifted will be Point(1, 1) and Point(359, 1)
   *      This crosses the anti-meridian, but creates a shape which is larger than the original. We therefore
   *      concluded that it does not need splitting.
   */
  def crossesAntimeridian(multi: MultiPolygon) = {
    val latLngFootprint = multi.reproject(WebMercator, LatLng)
    val longitudeShifted = MultiPolygon(
      latLngFootprint.polygons.map(
        poly => Polygon(poly.vertices.map(shiftPoint(_, 0, 1, false, 360)))
      ))
    latLngFootprint.area - 1 > longitudeShifted.area
  }

  def splitDataFootprintOverAntimeridian(
      multi: MultiPolygon,
      inputCRS: CRS,
      outputCRS: CRS): Projected[MultiPolygon] = {
    val latLngFootprint = multi.reproject(inputCRS, LatLng)
    val antiMeridian = Projected(Line(Point(180, -90), Point(180, 90)), 4326)
    val longitudeShifted = MultiPolygon(
      latLngFootprint.polygons.map(
        poly => Polygon(poly.vertices.map(shiftPoint(_, 0, 1, false, 360)))
      ))
    val leftAntiMeridianMask = Polygon(
      Array(Point(0, -90),
            Point(0, 90),
            Point(180, 90),
            Point(180, -90),
            Point(0, -90)))
    val leftOfAntimeridian =
      longitudeShifted.intersection(leftAntiMeridianMask).asMultiPolygon
    val rightOfAntimeridian = longitudeShifted
      .difference(leftAntiMeridianMask)
      .asMultiPolygon
      .map((multi) =>
        MultiPolygon(multi.polygons.map((poly) =>
          Polygon(poly.vertices.map(shiftPoint(_, 180, -1, true, -360))))));
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
      case _ =>
        throw new RuntimeException(
          s"Error while splitting data footprint over antimeridian: ${latLngFootprint.toGeoJson()}")
    }
  }

  def cloneTileFootprint(multi: MultiPolygon,
                         targetCRS: CRS): Projected[MultiPolygon] = {

    val latLngFootprint = multi.reproject(targetCRS, LatLng)
    val antiMeridian = Projected(Line(Point(180, -90), Point(180, 90)), 4326)
    val longitudeShifted = MultiPolygon(
      latLngFootprint.polygons.map(
        poly => Polygon(poly.vertices.map(shiftPoint(_, 0, 1, false, 360)))
      ))
    logger.debug(s"Cloning tile footprint")
    logger.debug(s"original footprint: ${latLngFootprint.toGeoJson()}")
    logger.debug(s"shifted footprint: ${longitudeShifted.toGeoJson()}")
    val leftClone = MultiPolygon(
      longitudeShifted.polygons.map(
        poly => Polygon(poly.vertices.map(p => Point(p.x - 360, p.y)))
      )
    )
    logger.debug(s"cloned footprint: ${leftClone.toGeoJson()}")

    val unioned = leftClone.union(longitudeShifted)
    (unioned.asMultiPolygon, targetCRS.epsgCode) match {
      case (Some(multi: MultiPolygon), Some(code: Int)) =>
        logger.debug(s"unioned footprint: ${multi.toGeoJson()}")
        Projected(multi.reproject(LatLng, targetCRS), code)
      case (a, b) =>
        throw new RuntimeException(
          s"Error cloning tile footprint across anti-meridian. Polygon: $a, epsg code: $b")
    }
  }

  def correctDataFootprint(intersects: Boolean,
                           dataFootprint: Option[MultiPolygon],
                           targetCRS: CRS): Option[Projected[MultiPolygon]] = {
    val correctedDataFootprint =
      (intersects, dataFootprint, targetCRS.epsgCode) match {
        case (true, Some(footprint), _) =>
          val newDataFootprint =
            splitDataFootprintOverAntimeridian(footprint, targetCRS, targetCRS)
          logger.debug(
            s"Split data footprint: ${newDataFootprint.geom.reproject(targetCRS, LatLng).toGeoJson()}")
          Some(newDataFootprint)
        case (false, Some(footprint), Some(code)) =>
          Some(Projected(footprint, code))
        case _ => None
      }
    correctedDataFootprint
  }

  def correctTileFootprint(intersects: Boolean,
                           tileFootprint: Option[MultiPolygon],
                           targetCRS: CRS): Option[Projected[MultiPolygon]] = {
    val correctedTileFootprint =
      (intersects, tileFootprint, targetCRS.epsgCode) match {
        case (true, Some(footprint), Some(code)) =>
          Some(cloneTileFootprint(footprint, targetCRS))
        case (false, Some(footprint), Some(code)) =>
          Some(Projected(footprint, code))
        case _ => None
      }
    correctedTileFootprint
  }
}
