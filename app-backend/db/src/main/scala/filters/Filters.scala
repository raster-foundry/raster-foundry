package com.rasterfoundry.database.filter

import com.rasterfoundry.datamodel._

import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import geotrellis.vector.{Polygon, Projected}

object Filters {

  def toolQP(toolParams: ToolQueryParameters): List[Option[Fragment]] = {
    List(toolParams.singleSource map { p =>
      fr"single_source = $p"
    })
  }

  def userQP(userParams: UserQueryParameters): List[Option[Fragment]] = {
    onlyUserQP(userParams.onlyUserParams) :::
      ownerQP(userParams.ownerParams) :::
      activationQP(userParams.activationParams)
  }

  def onlyUserQP(
      onlyUserParams: UserAuditQueryParameters
  ): List[Option[Fragment]] = {
    List(
      onlyUserParams.createdBy.map(cb => fr"created_by = $cb")
    )
  }

  def ownerQP(ownerParams: OwnerQueryParameters): List[Option[Fragment]] = {
    List(
      ownerParams.owner.toList.toNel
        .map({ owners =>
          Fragments.in(fr"owner", owners)
        })
    )
  }

  def organizationQP(orgParams: OrgQueryParameters): List[Option[Fragment]] = {
    val f1 = orgParams.organizations.toList.toNel
      .map(orgs => Fragments.in(fr"organization_id", orgs))
    List(f1)
  }

  def timestampQP(
      timestampParams: TimestampQueryParameters
  ): List[Option[Fragment]] = {
    val f1 = timestampParams.minCreateDatetime.map(
      minCreate => fr"created_at > $minCreate"
    )
    val f2 = timestampParams.maxCreateDatetime.map(
      maxCreate => fr"created_at < $maxCreate"
    )
    val f3 = timestampParams.minModifiedDatetime.map(
      minMod => fr"modified_at > $minMod"
    )
    val f4 = timestampParams.maxModifiedDatetime.map(
      maxMod => fr"modified_at < $maxMod"
    )
    List(f1, f2, f3, f4)
  }

  def imageQP(imageParams: ImageQueryParameters): List[Option[Fragment]] = {
    val f1 =
      imageParams.minRawDataBytes.map(
        minBytes => fr"raw_data_bytes > $minBytes"
      )
    val f2 =
      imageParams.maxRawDataBytes.map(
        maxBytes => fr"raw_data_bytes < $maxBytes"
      )
    val f3 =
      imageParams.minResolution.map(minRes => fr"resolution_meters > $minRes")
    val f4 =
      imageParams.maxResolution.map(maxRes => fr"resolution_meters < $maxRes")
    val f5 = imageParams.scene.toList.toNel.map(scenes =>
      Fragments.in(fr"scene", scenes))
    List(f1, f2, f3, f4, f5)
  }

  def mapTokenQP(
      mapTokenParams: MapTokenQueryParameters
  ): List[Option[Fragment]] = {
    val f1 = mapTokenParams.name.map(name => fr"name = $name")
    val f2 =
      mapTokenParams.projectId.map(projectId => fr"project_id = $projectId")
    List(f1, f2)
  }

  def thumbnailQP(
      thumbnailParams: ThumbnailQueryParameters
  ): List[Option[Fragment]] = {
    List(
      thumbnailParams.sceneId.map(sceneId => fr"scene_id = ${sceneId}")
    )
  }

  def searchQP(
      searchParams: SearchQueryParameters,
      cols: List[String]
  ): List[Option[Fragment]] =
    List(
      searchParams.search.getOrElse("") match {
        case "" => None
        case searchString =>
          val searchF: List[Option[Fragment]] = cols.map(col => {
            val patternString: String = "%" + searchString.toUpperCase() + "%"
            Some(Fragment.const(s"UPPER($col)") ++ fr"LIKE ${patternString}")
          })
          Some(
            Fragment.const("(") ++ Fragments
              .orOpt(searchF: _*) ++ Fragment.const(")")
          )
      }
    )

  def activationQP(
      activationParams: ActivationQueryParameters
  ): List[Option[Fragment]] = {
    List(activationParams.isActive.map(isActive => fr"is_active = ${isActive}"))
  }

  def platformIdQP(
      platformIdParams: PlatformIdQueryParameters
  ): List[Option[Fragment]] = {
    List(
      platformIdParams.platformId
        .map(platformId => fr"platform_id = ${platformId}")
    )
  }

  def metricQP(
      metricQueryParams: MetricQueryParameters
  ): List[Option[Fragment]] = {
    val requestTypeF = {
      metricQueryParams.requestType match {
        case MetricRequestType.ProjectMosaicRequest =>
          val keyFilter = "projectOwner"
          Some(fr"metrics.metric_event ?? $keyFilter")
        case MetricRequestType.AnalysisRequest =>
          val keyFilter = "analysisOwner"
          Some(fr"metrics.metric_event ?? $keyFilter")
      }
    }
    List(
      metricQueryParams.projectId map { projId =>
        {
          val jsString = s"""{"projectId":"$projId"}"""
          fr"metrics.metric_event @> $jsString :: jsonb"
        }
      },
      metricQueryParams.projectLayerId map { projLayerId =>
        val jsString = s"""{"projectLayerId":"$projLayerId"}"""
        fr"metrics.metric_event @> $jsString :: jsonb"
      },
      metricQueryParams.analysisId map { analysisId =>
        val jsString = s"""{"analysisId":"$analysisId"}"""
        fr"metrics.metric_event @> $jsString :: jsonb"
      },
      metricQueryParams.nodeId map { nodeId =>
        val jsString = s"""{"nodeId":"$nodeId"}"""
        fr"metrics.metric_event @> $jsString :: jsonb"
      },
      metricQueryParams.referer map { referer =>
        val jsString = s"""{"referer":"$referer"}"""
        fr"metrics.metric_event @> $jsString :: jsonb"
      },
      requestTypeF
    )
  }

  def taskQP(taskQP: TaskQueryParameters)(
      implicit putTaskStatus: Put[TaskStatus],
      putGeom: Put[Projected[Polygon]]
  ): List[Option[Fragment]] =
    List(
      taskQP.status map { qp =>
        fr"status = $qp "
      },
      taskQP.locked map {
        case true  => fr"locked_by IS NOT NULL"
        case false => fr"locked_by IS NULL"
      },
      taskQP.lockedBy map { qp =>
        fr"locked_by = $qp "
      },
      taskQP.bboxPolygon match {
        case Some(bboxPolygons) =>
          val fragments = bboxPolygons.map(
            bbox =>
              fr"(_ST_Intersects(geometry, ${bbox}) AND geometry && ${bbox})"
          )
          Some(fr"(" ++ Fragments.or(fragments: _*) ++ fr")")
        case _ => None
      },
      taskQP.actionUser map { qp =>
        fr"task_actions.user_id = $qp"
      },
      taskQP.actionType map { qp =>
        fr"task_actions.to_status = $qp"
      },
      taskQP.actionStartTime map { qp =>
        fr"task_actions.timestamp >= $qp"
      },
      taskQP.actionEndTime map { qp =>
        fr"task_actions.timestamp <= $qp"
      }
    )
}
