package com.azavea.rf.database.filter

import com.azavea.rf.datamodel._
import com.azavea.rf.database._

import doobie._, doobie.implicits._
import doobie.postgres._, doobie.postgres.implicits._
import cats._, cats.data._, cats.effect.IO, cats.implicits._

import Fragments.{ in, whereAndOpt }


object Filters {

  def userQP(userParams: UserQueryParameters): List[Option[Fragment]] = {
    val f1 = userParams.createdBy.map(cb => fr"created_by = $cb")
    val f2 = userParams.modifiedBy.map(mb => fr"modified_by = $mb")
    val f3 = userParams.owner.map(owner => fr"owner = $owner")
    List(f1, f2, f3)
  }

  def organizationQP(orgParams: OrgQueryParameters): List[Option[Fragment]] = {
    val f1 = orgParams.organizations.toList.toNel.map(orgs => in(fr"organizationId", orgs))
    List(f1)
  }

  def timestampQP(timestampParams: TimestampQueryParameters): List[Option[Fragment]] = {
    val f1 = timestampParams.minCreateDatetime.map(minCreate => fr"created_at > $minCreate")
    val f2 = timestampParams.maxCreateDatetime.map(maxCreate => fr"created_at < $maxCreate")
    val f3 = timestampParams.minModifiedDatetime.map(minMod => fr"modified_at > $minMod")
    val f4 = timestampParams.maxModifiedDatetime.map(maxMod => fr"modified_at < $maxMod")
    List(f1, f2, f3, f4)
  }

  def imageQP(imageParams: ImageQueryParameters): List[Option[Fragment]] = {
    val f1 = imageParams.minRawDataBytes.map(minBytes => fr"raw_data_bytes > minBytes")
    val f2 = imageParams.maxRawDataBytes.map(maxBytes => fr"raw_data_bytes < maxBytes")
    val f3 = imageParams.minResolution.map(minRes => fr"resolution_meters > minRes")
    val f4 = imageParams.maxResolution.map(maxRes => fr"resolution_meters < maxRes")
    val f5 = imageParams.scene.toList.toNel.map(scenes => in(fr"scene", scenes))
    List(f1, f2, f3, f4, f5)
  }

  def mapTokenQP(mapTokenParams: MapTokenQueryParameters): List[Option[Fragment]] = {
    val f1 = mapTokenParams.name.map(name => fr"name = $name")
    val f2 = mapTokenParams.projectId.map(projectId => fr"project_id = $projectId")
    List(f1, f2)
  }

  def thumbnailQP(thumbnailParams: ThumbnailQueryParameters): List[Option[Fragment]] = {
    List(
      thumbnailParams.sceneId.map(sceneId => fr"scene_id = ${sceneId}")
    )
  }
}

