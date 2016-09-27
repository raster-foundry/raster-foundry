package com.azavea.rf.scene

import java.sql.Timestamp

import com.lonelyplanet.akka.http.extensions.{PageRequest, Order}

import com.azavea.rf.datamodel.latest.schema.tables._
import com.azavea.rf.datamodel.driver.ExtendedPostgresDriver
import com.azavea.rf.utils.queryparams._


object SceneFilters {
  import ExtendedPostgresDriver.api._

  type ScenesQuery = Query[Scenes, Scenes#TableElementType, Seq]

  val datePart = SimpleFunction.binary[String, Option[Timestamp], Int]("date_part")

  implicit class SceneDefault[M, U, C[_]](scenes: ScenesQuery) {
    def filterByOrganization(orgParams: OrgQueryParameters): ScenesQuery = {
      if (orgParams.organizations.size > 0) {
        scenes.filter{ fp =>
          fp.organizationId inSet orgParams.organizations.toSet
        }
      } else {
        scenes
      }
    }

    def filterByUser(userParams: UserQueryParameters): ScenesQuery = {
      scenes.filter{ scene =>
        val userFilterConditions = List(
          userParams.createdBy.map(scene.createdBy === _),
          userParams.modifiedBy.map(scene.modifiedBy === _)
        )
        userFilterConditions
          .collect({case Some(criteria)  => criteria})
          .reduceLeftOption(_ && _)
          .getOrElse(true: Rep[Boolean])
      }
    }

    def filterBySceneParams(sceneParams: SceneQueryParameters): ScenesQuery = {
      scenes.filter{ scene =>
        val sceneFilterConditions = List(
          sceneParams.maxAcquisitionDatetime.map(scene.acquisitionDate < _),
          sceneParams.minAcquisitionDatetime.map(scene.acquisitionDate > _),
          sceneParams.maxCloudCover.map(scene.cloudCover < _),
          sceneParams.minCloudCover.map(scene.cloudCover > _),
          sceneParams.minSunAzimuth.map(scene.sunAzimuth > _),
          sceneParams.maxSunAzimuth.map(scene.sunAzimuth < _),
          sceneParams.minSunElevation.map(scene.sunElevation > _),
          sceneParams.maxSunElevation.map(scene.sunElevation < _)
        )
        sceneFilterConditions
          .collect({case Some(criteria)  => criteria})
          .reduceLeftOption(_ && _)
          .getOrElse(Some(true): Rep[Option[Boolean]])
      }.filter { scene =>
        sceneParams.month
          .map(datePart("month", scene.acquisitionDate) === _)
          .reduceLeftOption(_ || _)
          .getOrElse(true: Rep[Boolean])
      }.filter { scene =>
        sceneParams.datasource
          .map(scene.datasource === _)
          .reduceLeftOption(_ || _)
          .getOrElse(true: Rep[Boolean])
      }
    }

    def filterByTimestamp(timeParams: TimestampQueryParameters): ScenesQuery = {
      scenes.filter{ scene =>
        val timestampFilters = List(
          timeParams.minCreateDatetime.map(scene.createdAt > _),
          timeParams.maxCreateDatetime.map(scene.createdAt < _),
          timeParams.minModifiedDatetime.map(scene.modifiedAt > _),
          timeParams.maxModifiedDatetime.map(scene.modifiedAt < _)
        )
        timestampFilters
          .collect({case Some(criteria)  => criteria})
          .reduceLeftOption(_ && _)
          .getOrElse(true: Rep[Boolean])
      }
    }

    val datePart = SimpleFunction.binary[String, Option[Timestamp], Int]("date_part")

    def sort(sortMap: Map[String, Order]): ScenesQuery = {
      def applySort(query: ScenesQuery, sortMap: Map[String, Order]): ScenesQuery = {
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

          case Some(("datasource", Order.Asc)) => applySort(query.sortBy(_.datasource.asc),
            sortMap.tail)
          case Some(("datasource", Order.Desc)) => applySort(query.sortBy(_.datasource.desc),
            sortMap.tail)

          case Some(("month", Order.Asc)) => applySort(query.sortBy { scene =>
            datePart("month", scene.acquisitionDate).asc
          }, sortMap.tail)
          case Some(("month", Order.Desc)) => applySort(query.sortBy { scene =>
            datePart("month", scene.acquisitionDate).desc
          }, sortMap.tail)

          case Some(("acquisitionDatetime", Order.Asc)) => applySort(
            query.sortBy(_.acquisitionDate.asc), sortMap.tail)
          case Some(("acquisitionDatetime", Order.Desc)) => applySort(
            query.sortBy(_.acquisitionDate.desc), sortMap.tail)

          case Some(("sunAzimuth", Order.Asc)) => applySort(query.sortBy(_.sunAzimuth.asc),
            sortMap.tail)
          case Some(("sunAzimuth", Order.Desc)) => applySort(query.sortBy(_.sunAzimuth.desc),
            sortMap.tail)

          case Some(("sunElevation", Order.Asc)) => applySort(query.sortBy(_.sunElevation.asc),
            sortMap.tail)
          case Some(("sunElevation", Order.Desc)) => applySort(query.sortBy(_.sunElevation.desc),
            sortMap.tail)

          case Some(("cloudCover", Order.Asc)) => applySort(query.sortBy(_.cloudCover.asc),
            sortMap.tail)
          case Some(("cloudCover", Order.Desc)) => applySort(query.sortBy(_.cloudCover.desc),
            sortMap.tail)

          case Some(_) => applySort(query, sortMap.tail)
          case _ => query
        }
      }
      applySort(scenes, sortMap)
    }


    def page(pageRequest: PageRequest): ScenesQuery = {
      val sorted = scenes.sort(pageRequest.sort)
      sorted.drop(pageRequest.offset * pageRequest.limit).take(pageRequest.limit)
    }
  }
}
