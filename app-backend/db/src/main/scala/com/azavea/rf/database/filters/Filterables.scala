package com.azavea.rf.database.filter

import java.sql.Timestamp
import java.util.UUID

import com.azavea.rf.database.meta.RFMeta
import com.azavea.rf.database.Filterable
import com.azavea.rf.datamodel._
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.Fragments.in
import doobie.postgres._
import doobie.postgres.implicits._
import cats._
import cats.data._
import cats.effect.IO
import geotrellis.slick.Projected
import geotrellis.vector._

import com.typesafe.scalalogging.LazyLogging

trait Filterables extends RFMeta with LazyLogging {

  implicit val aoiQueryParamsFilter = Filterable[Any, AoiQueryParameters] { qp: AoiQueryParameters =>
      Filters.userQP(qp.userParams) ++
      Filters.timestampQP(qp.timestampParams)
  }

  implicit val permissionsFilter = Filterable[Any, User] { user: User =>
    val filter = Some(fr"owner = ${user.id}")
    List(filter)
  }

  implicit val orgFilters = Filterable[Any, List[UUID]] { orgIds: List[UUID] =>
    val f1: Option[doobie.Fragment] = orgIds.toNel.map(ids => in(fr"organization_id", ids))
    List(f1)
  }

  implicit val userQueryParamsFilter = Filterable[Any, UserQueryParameters] { userParams: UserQueryParameters =>
    Filters.userQP(userParams)
  }

  implicit val orgQueryParamsFilter = Filterable[Any, OrgQueryParameters] { orgParams: OrgQueryParameters =>
    Filters.organizationQP(orgParams)
  }

  implicit val timestampQueryParamsFilter = Filterable[Any, TimestampQueryParameters] { tsParams: TimestampQueryParameters =>
    Filters.timestampQP(tsParams)
  }

  implicit val imageQueryparamsFilter = Filterable[Any, ImageQueryParameters] { imgParams: ImageQueryParameters =>
    Filters.imageQP(imgParams)
  }

  implicit val projectQueryParametersFilter = Filterable[Any, ProjectQueryParameters] { projectParams: ProjectQueryParameters =>
    Filters.timestampQP(projectParams.timestampParams) ++
      Filters.userQP(projectParams.userParams) ++
      Filters.searchQP(projectParams.searchParams, List("name"))
  }

  implicit val CombinedToolQueryParametersFilter = Filterable[Any, CombinedToolQueryParameters] { toolParams: CombinedToolQueryParameters =>
    Filters.timestampQP(toolParams.timestampParams) ++
      Filters.userQP(toolParams.userParams) ++
      Filters.searchQP(toolParams.searchParams, List("title", "description"))
  }

  implicit val annotationQueryparamsFilter = Filterable[Any, AnnotationQueryParameters] { annotParams: AnnotationQueryParameters =>
    Filters.userQP(annotParams.userParams) ++ List(
      annotParams.label.map({ label => fr"label = $label" }),
      annotParams.machineGenerated.map({ mg => fr"machine_generated = $mg" }),
      annotParams.minConfidence.map({ minc => fr"min_confidence = $minc" }),
      annotParams.maxConfidence.map({ maxc => fr"max_confidence = $maxc" }),
      annotParams.quality.map({ quality => fr"quality = $quality" }),
      annotParams.annotationGroup.map({ ag => fr"annotation_group = $ag" })
    )
  }

  implicit val combinedSceneQueryParams = Filterable[Any, CombinedSceneQueryParams] { combineSceneParams: CombinedSceneQueryParams =>
    val sceneParams = combineSceneParams.sceneParams
    Filters.userQP(combineSceneParams.userParams) ++
    Filters.timestampQP(combineSceneParams.timestampParams) ++
      List(
        sceneParams.maxCloudCover.map({ mcc => fr"cloud_cover <= $mcc" }),
        sceneParams.minCloudCover.map({ mcc => fr"cloud_cover >= $mcc" }),
        sceneParams.minAcquisitionDatetime.map({ mac => fr"acquisition_date >= $mac" }),
        sceneParams.maxAcquisitionDatetime.map({ mac => fr"acquisition_date <= $mac" }),
        sceneParams.datasource.toList.toNel.map({ds => Fragments.in(fr"datasource", ds) }),
        sceneParams.month.toList.toNel.map(
          { months => Fragments.in(fr"date_part('month', acquisition_date)", months) }
        ),
        sceneParams.minDayOfMonth.map(
          { day => fr"date_part('day', acquisition_date) >= $day" }
        ),
        sceneParams.maxDayOfMonth.map(
          { day => fr"date_part('day', acquisition_date) >= $day" }
        ),
        sceneParams.maxSunAzimuth.map({ msa => fr"sun_azimuth <= ${msa}" }),
        sceneParams.minSunAzimuth.map({ msa => fr"sun_azimuth >= ${msa}" }),
        sceneParams.maxSunElevation.map({ mse => fr"sun_elevation <= ${mse}" }),
        sceneParams.minSunElevation.map({ mse => fr"sun_elevation >= ${mse}" }),
        sceneParams.ingested.map({
          case true => fr"ingest_status = 'INGESTED'"
          case _ => fr"ingest_status != 'INGESTED'"
        }),
        sceneParams.ingestStatus.toList.toNel.map({
          statuses => Fragments.in(fr"ingest_status", statuses)
        }),
        (sceneParams.bboxPolygon, sceneParams.shape) match {
          case (_, Some(shpId)) => None
          case (Some(bboxPolygons), _) => {
            val fragments = bboxPolygons.map(bbox =>
              fr"(_ST_Intersects(data_footprint, ${bbox}) AND tile_footprint && ${bbox})")
            Some(fr"(" ++ Fragments.or(fragments: _*) ++ fr")")
          }
          case _ => None
        }
      )
  }

  implicit val mapTokenQueryParametersFilter = Filterable[Any, CombinedMapTokenQueryParameters] { mapTokenParams: CombinedMapTokenQueryParameters =>
    Filters.userQP(mapTokenParams.userParams) ++
    Filters.mapTokenQP(mapTokenParams.mapTokenParams)
  }

  implicit val combinedToolCategoryParamsFilter =
    Filterable[Any, CombinedToolCategoryQueryParams] { ctcQP: CombinedToolCategoryQueryParams =>
      Filters.timestampQP(ctcQP.timestampParams) :+
      ctcQP.toolCategoryParams.search.map({ search => fr"category ILIKE $search" })
    }

  implicit val combinedToolRunQueryParameters =
    Filterable[Any, CombinedToolRunQueryParameters] { combinedToolRunParams: CombinedToolRunQueryParameters =>
      Filters.userQP(combinedToolRunParams.userParams) ++
        Filters.timestampQP(combinedToolRunParams.timestampParams) ++
        List(
          combinedToolRunParams.toolRunParams.createdBy.map({createdBy => fr"created_by = ${createdBy}"}),
          combinedToolRunParams.toolRunParams.projectId.map({projectId => fr"project_id = ${projectId}"}),
          combinedToolRunParams.toolRunParams.toolId.map({toolId => fr"tool_id = ${toolId}"})
        )
    }

  implicit val fragmentFilter = Filterable[Any, Fragment] { fragment: Fragment => List(Some(fragment)) }

  implicit def maybeTFilter[T] (implicit filterable: Filterable[Any, T]) = Filterable[Any, Option[T]] { maybeT: Option[T] =>
    maybeT match {
      case None => List.empty[Option[Fragment]]
      case Some(thing) => filterable.toFilters(thing)
    }
  }

  implicit def listTFilter[T] (implicit filterable: Filterable[Any, T]) = Filterable[Any, List[T]] { someFilterables: List[T] => {
    someFilterables.map(filterable.toFilters).foldLeft(List.empty[Option[Fragment]])(_ ++ _)
  }}

  implicit val datasourceQueryparamsFilter = Filterable[Any, DatasourceQueryParameters] { dsParams: DatasourceQueryParameters =>
    Filters.searchQP(dsParams.searchParams, List("name")) ++
      Filters.userQP(dsParams.userParams)
  }

  implicit val uploadQueryParameters = Filterable[Any, UploadQueryParameters] {uploadParams: UploadQueryParameters =>
    List(
      uploadParams.datasource.map({ ds => fr"datasource = ${ds}"}),
      uploadParams.organization.map({ organization => fr"organization_id = ${organization}"}),
      uploadParams.uploadStatus.map({ uploadStatus => fr"upload_status = ${uploadStatus}"})
    )
  }

  implicit val exportQueryparamsFilter = Filterable[Any, ExportQueryParameters] { exportParams: ExportQueryParameters =>
    List(
      exportParams.organization.map({ orgId => fr"organization = $orgId"}),
      exportParams.project.map({ projId => fr"project_id = $projId"}),
      exportParams.exportStatus.toList.toNel.map({ statuses =>
        val exportStatuses = statuses.map({ status =>
          try ExportStatus.fromString(status)
          catch {
            case e : Exception => throw new IllegalArgumentException(s"Invalid Ingest Status: $status")
          }
        })
        Fragments.in(fr"export_status", exportStatuses)
      })
    )
  }

  implicit val shapeQueryparamsFilter = Filterable[Any, ShapeQueryParameters] { shapeParams: ShapeQueryParameters =>
    Filters.timestampQP(shapeParams.timestampParams) ++
      Filters.userQP(shapeParams.userParams)
  }

  implicit val combinedImageQueryparamsFilter = Filterable[Any, CombinedImageQueryParams] { cips: CombinedImageQueryParams =>
    Filters.timestampQP(cips.timestampParams) ++ Filters.imageQP(cips.imageParams)
  }

  implicit val thumbnailParamsFilter = Filterable[Any, ThumbnailQueryParameters] { params: ThumbnailQueryParameters =>
    Filters.thumbnailQP(params)
  }

  implicit val teamQueryparamsFilter = Filterable[Any, TeamQueryParameters] { params: TeamQueryParameters =>
    Filters.timestampQP(params.timestampParams) ++
      Filters.onlyUserQP(params.onlyUserParams) ++
      Filters.searchQP(params.searchParams, List("name")) ++
      Filters.activationQP(params.activationParams)
  }

  implicit val platformQueryparamsFilter = Filterable[Any, PlatformQueryParameters] { params: PlatformQueryParameters =>
    Filters.timestampQP(params.timestampParams) ++
    Filters.onlyUserQP(params.onlyUserParams) ++
    Filters.searchQP(params.searchParams, List("name")) ++
    Filters.activationQP(params.activationParams)
  }

  implicit val organizationQueryparamsFilter = Filterable[Any, OrganizationQueryParameters] { params: OrganizationQueryParameters =>
    Filters.timestampQP(params.timestampParams) ++
    Filters.searchQP(params.searchParams, List("name")) ++
    Filters.activationQP(params.activationParams) ++
    Filters.platformIdQP(params.platformIdParams)
  }

  implicit val orgSearchQueryParamsFilter = Filterable[Organization, SearchQueryParameters] { params: SearchQueryParameters =>
    Filters.searchQP(params, List("name"))
  }

  implicit val userSearchQueryParamsFilter = Filterable[User, SearchQueryParameters] { params: SearchQueryParameters =>
    Filters.searchQP(params, List("name", "email"))
  }

  implicit def projectedGeometryFilter = Filterable[Any, Projected[Geometry]] {
    geom => List(Some(fr"ST_Intersects(data_footprint, ${geom})"))
  }

  implicit def projectedMultiPolygonFilter = Filterable[Any, Projected[MultiPolygon]] {
    geom => List(Some(fr"ST_Intersects(data_footprint, ${geom})"))
  }

}

object Filterables extends Filterables
