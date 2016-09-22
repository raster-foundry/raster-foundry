package com.azavea.rf.bucket

import com.lonelyplanet.akka.http.extensions.{PageRequest, Order}

import com.azavea.rf.datamodel.driver.ExtendedPostgresDriver
import com.azavea.rf.datamodel.latest.schema.tables._
import com.azavea.rf.utils.queryparams._


object BucketFilters {

  import ExtendedPostgresDriver.api._

  type BucketsQuery = Query[Buckets, Buckets#TableElementType, Seq]

  implicit class BucketDefault[M, U, C[_]](buckets: BucketsQuery) {

    def filterByOrganization(orgParams: OrgQueryParameters): BucketsQuery = {
      buckets.filter{
        bucket => orgParams.organization.map(bucket.organizationId === _)
          .reduceLeftOption(_ || _).getOrElse(true: Rep[Boolean])
      }
    }

    def filterByUser(userParams: UserQueryParameters): BucketsQuery = {
      buckets.filter{
        bucket => List(
          userParams.createdBy.map(bucket.createdBy === _),
          userParams.modifiedBy.map(bucket.modifiedBy === _)
        ).collect({case Some(criteria)  => criteria}).reduceLeftOption(_ && _).getOrElse(true: Rep[Boolean])
      }
    }

    def filterByTimestamp(timeParams: TimestampQueryParameters): BucketsQuery = {
      buckets.filter{ bucket =>
        List(
          timeParams.minCreateDatetime.map(bucket.createdAt > _),
          timeParams.maxCreateDatetime.map(bucket.createdAt < _),
          timeParams.minModifiedDatetime.map(bucket.modifiedAt > _),
          timeParams.maxModifiedDatetime.map(bucket.modifiedAt < _)
        ).collect({case Some(criteria)  => criteria}).reduceLeftOption(_ && _).getOrElse(true: Rep[Boolean])
      }
    }

    def sort(sortMap: Map[String, Order]): BucketsQuery = {
      def applySort(query: BucketsQuery, sortMap: Map[String, Order]): BucketsQuery = {
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

          case Some(("name", Order.Asc)) => applySort(query.sortBy(_.name.asc),
            sortMap.tail)
          case Some(("name", Order.Desc)) => applySort(query.sortBy(_.name.desc),
            sortMap.tail)

          case Some(("visibility", Order.Asc)) => applySort(query.sortBy(_.visibility.asc),
            sortMap.tail)
          case Some(("visibility", Order.Desc)) => applySort(query.sortBy(_.visibility.desc),
            sortMap.tail)

          case Some(_) => applySort(query, sortMap.tail)
          case _ => query
        }
      }
      applySort(buckets, sortMap)
    }

    def page(pageRequest: PageRequest): BucketsQuery = {
      val sorted = buckets.sort(pageRequest.sort)
      sorted.drop(pageRequest.offset * pageRequest.limit).take(pageRequest.limit)
    }
  }
}
