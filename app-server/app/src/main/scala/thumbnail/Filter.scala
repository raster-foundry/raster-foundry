package com.azavea.rf.thumbnail

import com.lonelyplanet.akka.http.extensions.{PageRequest, Order}

import com.azavea.rf.datamodel.driver.ExtendedPostgresDriver
import com.azavea.rf.datamodel.latest.schema.tables._
import com.azavea.rf.utils.queryparams._

object ThumbnailFilters {

  import ExtendedPostgresDriver.api._

  type ThumbnailsQuery = Query[Thumbnails, Thumbnails#TableElementType, Seq]

  implicit class ThumbnailDefault[M, U, C[_]](thumbnails: ThumbnailsQuery) {

    /** Filter thumbnails by sceneId
      *
      * Thumbnails can only be filtered by sceneId
      */
    def filterBySceneParams(sceneParams: ThumbnailQueryParameters): ThumbnailsQuery = {
        thumbnails.filter(_.scene === sceneParams.sceneId)
    }

    def sort(sortMap: Map[String, Order]): ThumbnailsQuery = {
      def applySort(query: ThumbnailsQuery, sortMap: Map[String, Order]): ThumbnailsQuery = {
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

          case Some(_) => applySort(query, sortMap.tail)
          case _ => query
        }
      }
      applySort(thumbnails, sortMap)
    }

    def page(pageRequest: PageRequest): ThumbnailsQuery = {
      val sorted = thumbnails.sort(pageRequest.sort)
      sorted.drop(pageRequest.offset * pageRequest.limit).take(pageRequest.limit)
    }
  }
}
