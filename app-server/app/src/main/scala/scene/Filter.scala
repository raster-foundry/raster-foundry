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

  type SceneJoinQuery = Query[(Scenes, Rep[Option[Footprints]], Rep[Option[Images]], Rep[Option[Thumbnails]]),(ScenesRow, Option[FootprintsRow], Option[ImagesRow], Option[ThumbnailsRow]), Seq]

  implicit class SceneJoin[M, U, C[_]](sceneJoin: SceneJoinQuery) {

    /** Handle pagination with inner join on filtered scenes
      *
      * Pagination must be handled with an inner join here because the results
      * have duplicate scenes since there are many thumbnails + images per scene
      * potentially that have to be grouped server side.
      *
      * Filtering has to happen here because we need to filter the paginated results
      * and then do the inner join on those results
      */
    def page(combinedParams: CombinedSceneQueryParams, pageRequest: PageRequest): SceneJoinQuery = {
      val pagedScenes = Scenes
        .filterByOrganization(combinedParams.orgParams)
        .filterByUser(combinedParams.userParams)
        .filterByTimestamp(combinedParams.timestampParams)
        .filterBySceneParams(combinedParams.sceneParams)
        .sort(pageRequest.sort)
        .drop(pageRequest.offset * pageRequest.limit)
        .take(pageRequest.limit)
      val joinedResults = for {
        (pagedScene, join) <- pagedScenes join sceneJoin on (_.id === _._1.id)
      } yield (join)

      // Need to sort after the join because the join removes the sort order
      joinedResults.sort(pageRequest.sort)
    }

    def sort(sortMap: Map[String, Order]): SceneJoinQuery = {
      def applySort(query: SceneJoinQuery, sortMap: Map[String, Order]): SceneJoinQuery = {
        sortMap.headOption match {
          case Some(("createdAt", Order.Asc)) => applySort(query.sortBy(_._1.createdAt.asc),
            sortMap.tail)
          case Some(("createdAt", Order.Desc)) => applySort(query.sortBy(_._1.createdAt.desc),
            sortMap.tail)

          case Some(("modifiedAt", Order.Asc)) => applySort(query.sortBy(_._1.modifiedAt.asc),
            sortMap.tail)
          case Some(("modifiedAt", Order.Desc)) => applySort(query.sortBy(_._1.modifiedAt.desc),
            sortMap.tail)

          case Some(("organization", Order.Asc)) => applySort(query.sortBy(_._1.organizationId.asc),
            sortMap.tail)
          case Some(("organization", Order.Desc)) => applySort(query.sortBy(_._1.organizationId.desc),
            sortMap.tail)

          case Some(("datasource", Order.Asc)) => applySort(query.sortBy(_._1.datasource.asc),
            sortMap.tail)
          case Some(("datasource", Order.Desc)) => applySort(query.sortBy(_._1.datasource.desc),
            sortMap.tail)

          case Some(("month", Order.Asc)) => applySort(query.sortBy { join =>
            datePart("month", join._1.acquisitionDate).asc
          }, sortMap.tail)
          case Some(("month", Order.Desc)) => applySort(query.sortBy { join =>
            datePart("month", join._1.acquisitionDate).desc
          }, sortMap.tail)

          case Some(("acquisitionDatetime", Order.Asc)) => applySort(
            query.sortBy(_._1.acquisitionDate.asc), sortMap.tail)
          case Some(("acquisitionDatetime", Order.Desc)) => applySort(
            query.sortBy(_._1.acquisitionDate.desc), sortMap.tail)

          case Some(("sunAzimuth", Order.Asc)) => applySort(query.sortBy(_._1.sunAzimuth.asc),
            sortMap.tail)
          case Some(("sunAzimuth", Order.Desc)) => applySort(query.sortBy(_._1.sunAzimuth.desc),
            sortMap.tail)

          case Some(("sunElevation", Order.Asc)) => applySort(query.sortBy(_._1.sunElevation.asc),
            sortMap.tail)
          case Some(("sunElevation", Order.Desc)) => applySort(query.sortBy(_._1.sunElevation.desc),
            sortMap.tail)

          case Some(("cloudCover", Order.Asc)) => applySort(query.sortBy(_._1.cloudCover.asc),
            sortMap.tail)
          case Some(("cloudCover", Order.Desc)) => applySort(query.sortBy(_._1.cloudCover.desc),
            sortMap.tail)

          case Some(_) => applySort(sceneJoin, sortMap.tail)
          case _ => query
        }
      }
      applySort(sceneJoin, sortMap)
    }
  }

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



  /** Return a join query for scenes
    *
    * @sceneQuery ScenesQuery base scenes query
    */
    def joinWithRelated = {
      for {
        (((scene, footprint), image), thumbnail) <-
        (scenes
          joinLeft Footprints on (_.id === _.sceneId)
          joinLeft Images on (_._1.id === _.scene)
          joinLeft Thumbnails on (_._1._1.id === _.scene))
      } yield( scene, footprint, image, thumbnail )
    }

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

          case Some(("month", Order.Asc)) => applySort(query.sortBy { join =>
            datePart("month", join.acquisitionDate).asc
          }, sortMap.tail)
          case Some(("month", Order.Desc)) => applySort(query.sortBy { join =>
            datePart("month", join.acquisitionDate).desc
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

  }
}
