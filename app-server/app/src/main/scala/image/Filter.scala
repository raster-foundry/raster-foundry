package com.azavea.rf.image

import com.lonelyplanet.akka.http.extensions.{PageRequest, Order}

import com.azavea.rf.datamodel.driver.ExtendedPostgresDriver
import com.azavea.rf.datamodel.latest.schema.tables._
import com.azavea.rf.utils.queryparams._


object ImageFilters {
  import ExtendedPostgresDriver.api._

  type ImagesQuery = Query[Images, Images#TableElementType, Seq]

  implicit class ImageDefault[M, U, C[_]](images: ImagesQuery) {

    def filterByImageParams(imageParams: ImageQueryParameters): ImagesQuery = {
      images.filter{ image =>
        val imageFilterConditions = List(
          imageParams.minRawDataBytes.map(image.rawDataBytes > _),
          imageParams.maxRawDataBytes.map(image.rawDataBytes < _)
        )
        imageFilterConditions
          .collect({case Some(criteria)  => criteria})
          .reduceLeftOption(_ && _).getOrElse(true: Rep[Boolean])
      }.filter{ image =>
        imageParams.scene
          .map(image.scene === _)
          .reduceLeftOption(_ || _)
          .getOrElse(true: Rep[Boolean])
      }
    }

    def filterByOrganization(orgParams: OrgQueryParameters): ImagesQuery = {
      if (orgParams.organizations.size > 0) {
        images.filter{ image =>
          image.organizationId inSet orgParams.organizations.toSet
        }
      } else {
        images
      }
    }

    def filterByTimestamp(timeParams: TimestampQueryParameters): ImagesQuery = {
      images.filter{ image =>
        val timestampFilters = List(
          timeParams.minCreateDatetime.map(image.createdAt > _),
          timeParams.maxCreateDatetime.map(image.createdAt < _),
          timeParams.minModifiedDatetime.map(image.modifiedAt > _),
          timeParams.maxModifiedDatetime.map(image.modifiedAt < _)
        )
        timestampFilters
          .collect({case Some(criteria)  => criteria})
          .reduceLeftOption(_ && _)
          .getOrElse(true: Rep[Boolean])
      }
    }

    def sort(sortMap: Map[String, Order]): ImagesQuery = {
      def applySort(query: ImagesQuery, sortMap: Map[String, Order]): ImagesQuery = {
        sortMap.headOption match {
          case Some(("createdAt", Order.Asc)) => applySort(query.sortBy(_.createdAt.asc),
            sortMap.tail)
          case Some(("createdAt", Order.Desc)) => applySort(query.sortBy(_.createdAt.desc),
            sortMap.tail)

          case Some(("modifiedAt", Order.Asc)) => applySort(query.sortBy(_.modifiedAt.asc),
            sortMap.tail)
          case Some(("modifiedAt", Order.Desc)) => applySort(query.sortBy(_.modifiedAt.desc),
            sortMap.tail)

          case Some(("organization", Order.Asc)) => applySort(query.sortBy(_.organizationId.asc),
            sortMap.tail)
          case Some(("organization", Order.Desc)) => applySort(query.sortBy(_.organizationId.desc),
            sortMap.tail)

          case Some(("visibility", Order.Asc)) => applySort(query.sortBy(_.visibility.asc),
            sortMap.tail)
          case Some(("visibility", Order.Desc)) => applySort(query.sortBy(_.visibility.desc),
            sortMap.tail)

          case Some(("rawDataBytes", Order.Asc)) => applySort(query.sortBy(_.rawDataBytes.asc),
            sortMap.tail)
          case Some(("rawDataBytes", Order.Desc)) => applySort(query.sortBy(_.rawDataBytes.desc),
            sortMap.tail)

          case Some(_) => applySort(query, sortMap.tail)
          case _ => query
        }
      }
      applySort(images, sortMap)
    }

    def page(pageRequest: PageRequest): ImagesQuery = {
      val sorted = images.sort(pageRequest.sort)
      sorted
        .drop(pageRequest.offset * pageRequest.limit)
        .take(pageRequest.limit)
    }

  }

}
