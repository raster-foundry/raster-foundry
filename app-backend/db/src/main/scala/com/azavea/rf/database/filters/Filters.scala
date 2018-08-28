package com.azavea.rf.database.filter

import com.azavea.rf.datamodel._
import com.azavea.rf.database._

import doobie._, doobie.implicits._
import doobie.postgres._, doobie.postgres.implicits._
import cats._, cats.data._, cats.effect.IO, cats.implicits._

import Fragments.{in, whereAndOpt}

object Filters {

  def userQP(userParams: UserQueryParameters): List[Option[Fragment]] = {
    onlyUserQP(userParams.onlyUserParams) :::
      ownerQP(userParams.ownerParams) :::
      activationQP(userParams.activationParams)
  }

  def onlyUserQP(
      onlyUserParams: UserAuditQueryParameters): List[Option[Fragment]] = {
    List(
      onlyUserParams.createdBy.map(cb => fr"created_by = $cb"),
      onlyUserParams.modifiedBy.map(mb => fr"modified_by = $mb")
    )
  }

  def ownerQP(ownerParams: OwnerQueryParameters): List[Option[Fragment]] = {
    List(ownerParams.owner.map(owner => fr"owner = $owner"))
  }

  def organizationQP(orgParams: OrgQueryParameters): List[Option[Fragment]] = {
    val f1 = orgParams.organizations.toList.toNel.map(orgs =>
      in(fr"organization_id", orgs))
    List(f1)
  }

  def timestampQP(
      timestampParams: TimestampQueryParameters): List[Option[Fragment]] = {
    val f1 = timestampParams.minCreateDatetime.map(minCreate =>
      fr"created_at > $minCreate")
    val f2 = timestampParams.maxCreateDatetime.map(maxCreate =>
      fr"created_at < $maxCreate")
    val f3 = timestampParams.minModifiedDatetime.map(minMod =>
      fr"modified_at > $minMod")
    val f4 = timestampParams.maxModifiedDatetime.map(maxMod =>
      fr"modified_at < $maxMod")
    List(f1, f2, f3, f4)
  }

  def imageQP(imageParams: ImageQueryParameters): List[Option[Fragment]] = {
    val f1 =
      imageParams.minRawDataBytes.map(minBytes => fr"raw_data_bytes > minBytes")
    val f2 =
      imageParams.maxRawDataBytes.map(maxBytes => fr"raw_data_bytes < maxBytes")
    val f3 =
      imageParams.minResolution.map(minRes => fr"resolution_meters > minRes")
    val f4 =
      imageParams.maxResolution.map(maxRes => fr"resolution_meters < maxRes")
    val f5 = imageParams.scene.toList.toNel.map(scenes => in(fr"scene", scenes))
    List(f1, f2, f3, f4, f5)
  }

  def mapTokenQP(
      mapTokenParams: MapTokenQueryParameters): List[Option[Fragment]] = {
    val f1 = mapTokenParams.name.map(name => fr"name = $name")
    val f2 =
      mapTokenParams.projectId.map(projectId => fr"project_id = $projectId")
    List(f1, f2)
  }

  def thumbnailQP(
      thumbnailParams: ThumbnailQueryParameters): List[Option[Fragment]] = {
    List(
      thumbnailParams.sceneId.map(sceneId => fr"scene_id = ${sceneId}")
    )
  }

  def searchQP(searchParams: SearchQueryParameters,
               cols: List[String]): List[Option[Fragment]] = {
    List(
      searchParams.search.map(valueToMatch => {
        Fragment.const(
          "(" ++ cols
            .map(col => {
              val namePattern: String = "'%" + valueToMatch.toUpperCase() + "%'"
              s"UPPER($col) LIKE $namePattern"
            })
            .mkString(" OR ") ++ ")"
        )
      })
    )
  }

  def activationQP(
      activationParams: ActivationQueryParameters): List[Option[Fragment]] = {
    List(activationParams.isActive.map(isActive => fr"is_active = ${isActive}"))
  }

  def platformIdQP(
      platformIdParams: PlatformIdQueryParameters): List[Option[Fragment]] = {
    List(platformIdParams.platformId.map(platformId =>
      fr"platform_id = ${platformId}"))
  }
}
