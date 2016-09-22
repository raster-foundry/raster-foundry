package com.azavea.rf.footprint

import java.sql.Timestamp

import com.lonelyplanet.akka.http.extensions.{PageRequest, Order}
import geotrellis.slick.Projected
import geotrellis.vector.{Point, Polygon}

import com.azavea.rf.datamodel.latest.schema.tables._
import com.azavea.rf.datamodel.driver.ExtendedPostgresDriver
import com.azavea.rf.utils.queryparams._
import com.azavea.rf.utils.UserErrorException

object FootprintFilters {
  import ExtendedPostgresDriver.api._

  type FootprintsQuery = Query[Footprints, Footprints#TableElementType, Seq]

  val datePart = SimpleFunction.binary[String, Option[Timestamp], Int]("date_part")

  implicit class FootprintDefault[M, U, C[_]](footprints: FootprintsQuery) {
    def filterByOrganization(orgParams: OrgQueryParameters): FootprintsQuery = {
      if (orgParams.organizations.size > 0) {
        footprints.filter{ fp =>
          fp.organizationId inSet orgParams.organizations.toSet
        }
      } else {
        footprints
      }
    }

    def filterByTimestamp(timeParams: TimestampQueryParameters): FootprintsQuery = {
      footprints.filter{ footprint =>
        List(
          timeParams.minCreateDatetime.map(footprint.createdAt > _),
          timeParams.maxCreateDatetime.map(footprint.createdAt < _),
          timeParams.minModifiedDatetime.map(footprint.modifiedAt > _),
          timeParams.maxModifiedDatetime.map(footprint.modifiedAt < _)
        ).collect({case Some(criteria) => criteria}).reduceLeftOption(_ && _)
          .getOrElse(true: Rep[Boolean])
      }
    }

    def filterByFootprintParams(footprintParams: FootprintQueryParameters): FootprintsQuery = {
      val intersectFilter = (footprintParams.x, footprintParams.y) match {
        case (Some(x), Some(y)) =>
          val coords = Projected(Point(x, y), 3857)
          footprints.filter(_.multipolygon.intersects(coords))
        case (Some(x), None) => throw new UserErrorException(
          "Both coordinate parameters (x, y) must be specified"
        )
        case (None, Some(lng)) => throw new UserErrorException(
          "Both coordinate parameters (x, y) must be specified"
        )
        case _ => footprints
      }
      footprintParams.bbox match {
        case Some(s) =>
          val coords = s.split(",")
          val xmin = coords(0).toDouble
          val xmax = coords(2).toDouble
          val ymin = coords(1).toDouble
          val ymax = coords(3).toDouble
          val p1 = Point(xmin, ymin)
          val p2 = Point(xmax, ymin)
          val p3 = Point(xmax, ymax)
          val p4 = Point(xmin, ymax)
          val bbox = Projected(Polygon(Seq(p1,p2,p3,p4,p1)), 3857)
          footprints.filter(_.multipolygon.intersects(bbox))
        case _ => intersectFilter
      }
    }

    def sort(sortMap: Map[String, Order]): FootprintsQuery = {
      def applySort(query: FootprintsQuery, sortMap: Map[String, Order]): FootprintsQuery = {
        sortMap.headOption match {
          case Some(("createdAt", Order.Asc)) => applySort(
            query.sortBy(_.createdAt.asc), sortMap.tail)
          case Some(("createdAt", Order.Desc)) => applySort(
            query.sortBy(_.createdAt.desc), sortMap.tail)
          case Some(("modifiedAt", Order.Asc)) => applySort(
            query.sortBy(_.modifiedAt.asc), sortMap.tail)
          case Some(("modifiedAt", Order.Desc)) => applySort(
            query.sortBy(_.modifiedAt.desc), sortMap.tail)
          case Some(("organization", Order.Asc)) => applySort(
            query.sortBy(_.organizationId.asc), sortMap.tail)
          case Some(("organization", Order.Desc)) => applySort(
            query.sortBy(_.organizationId.desc), sortMap.tail)
          case Some(_) => applySort(query, sortMap.tail)
          case _ => query
        }
      }
      applySort(footprints, sortMap)
    }

    def page(pageRequest: PageRequest): FootprintsQuery = {
      val sorted = footprints.sort(pageRequest.sort)
      sorted.drop(pageRequest.offset * pageRequest.limit).take(pageRequest.limit)
    }
  }
}
