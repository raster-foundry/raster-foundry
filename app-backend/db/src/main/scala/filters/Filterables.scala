package com.rasterfoundry.database.filter

import com.rasterfoundry.database.Filterable
import com.rasterfoundry.database.meta.RFMeta
import com.rasterfoundry.datamodel._

import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import doobie.Fragments.in
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import geotrellis.vector.{io => _, _}

import java.util.UUID

@SuppressWarnings(Array("CatchException", "ListAppend"))
trait Filterables extends RFMeta with LazyLogging {

  implicit val permissionsFilter: Filterable[Any, User] =
    Filterable[Any, User] { user: User =>
      val filter = Some(fr"owner = ${user.id}")
      List(filter)
    }

  implicit val orgFilters: Filterable[Any, List[UUID]] =
    Filterable[Any, List[UUID]] { orgIds: List[UUID] =>
      val f1: Option[doobie.Fragment] =
        orgIds.toNel.map(ids => in(fr"organization_id", ids))
      List(f1)
    }

  implicit val idFilter: Filterable[Any, UUID] =
    Filterable[Any, UUID] { id: UUID =>
      List(Some(fr"id = $id"))
    }

  implicit val userQueryParamsFilter: Filterable[Any, UserQueryParameters] =
    Filterable[Any, UserQueryParameters] { userParams: UserQueryParameters =>
      Filters.userQP(userParams)
    }

  implicit val orgQueryParamsFilter: Filterable[Any, OrgQueryParameters] =
    Filterable[Any, OrgQueryParameters] { orgParams: OrgQueryParameters =>
      Filters.organizationQP(orgParams)
    }

  implicit val timestampQueryParamsFilter
    : Filterable[Any, TimestampQueryParameters] =
    Filterable[Any, TimestampQueryParameters] {
      tsParams: TimestampQueryParameters =>
        Filters.timestampQP(tsParams)
    }

  implicit val imageQueryparamsFilter: Filterable[Any, ImageQueryParameters] =
    Filterable[Any, ImageQueryParameters] { imgParams: ImageQueryParameters =>
      Filters.imageQP(imgParams)
    }

  implicit val projectQueryParametersFilter
    : Filterable[Any, ProjectQueryParameters] =
    Filterable[Any, ProjectQueryParameters] {
      projectParams: ProjectQueryParameters =>
        Filters.timestampQP(projectParams.timestampParams) ++
          Filters.userQP(projectParams.userParams) ++
          Filters.searchQP(projectParams.searchParams, List("name")) ++
          List(
            projectParams.tagQueryParameters.tagsInclude.toList.toNel.map({
              tags =>
                val tagsIncludeF = tags
                  .map({ tag =>
                    fr"${tag} = ANY (projects.tags)"
                  })
                  .toList
                fr"(" ++ Fragments.or(tagsIncludeF: _*) ++ fr")"
            }),
            projectParams.tagQueryParameters.tagsExclude.toList.toNel.map({
              tags =>
                val tagsIncludeF = tags
                  .map({ tag =>
                    fr"${tag} = ANY (projects.tags)"
                  })
                  .toList
                fr"(NOT (" ++ Fragments.or(tagsIncludeF: _*) ++ fr"))"
            })
          )
    }

  implicit val CombinedToolQueryParametersFilter
    : Filterable[Any, CombinedToolQueryParameters] =
    Filterable[Any, CombinedToolQueryParameters] {
      toolParams: CombinedToolQueryParameters =>
        Filters.timestampQP(toolParams.timestampParams) ++
          Filters.userQP(toolParams.userParams) ++
          Filters.searchQP(
            toolParams.searchParams,
            List("title", "description")
          ) ++
          Filters.toolQP(toolParams.toolParams)
    }

  implicit val annotationQueryparamsFilter
    : Filterable[Any, AnnotationQueryParameters] =
    Filterable[Any, AnnotationQueryParameters] {
      annotParams: AnnotationQueryParameters =>
        Filters.userQP(annotParams.userParams) ++
          List(
            annotParams.label.map({ label =>
              fr"label = $label"
            }),
            annotParams.machineGenerated.map({ mg =>
              fr"machine_generated = $mg"
            }),
            annotParams.minConfidence.map({ minc =>
              fr"min_confidence = $minc"
            }),
            annotParams.maxConfidence.map({ maxc =>
              fr"max_confidence = $maxc"
            }),
            annotParams.quality.map({ quality =>
              fr"quality = $quality"
            }),
            annotParams.annotationGroup.map({ ag =>
              fr"annotation_group = $ag"
            }),
            annotParams.taskId.map({ tid =>
              fr"task_id = $tid"
            }),
            annotParams.bboxPolygon match {
              case Some(bboxPolygons) =>
                val fragments = bboxPolygons.map(
                  bbox =>
                    fr"(_ST_Intersects(geometry, ${bbox}) AND geometry && ${bbox})"
                )
                Some(fr"(" ++ Fragments.or(fragments: _*) ++ fr")")
              case _ => None
            }
          )
    }

  implicit val combinedSceneQueryParams
    : Filterable[Any, CombinedSceneQueryParams] =
    Filterable[Any, CombinedSceneQueryParams] {
      combineSceneParams: CombinedSceneQueryParams =>
        val sceneParams = combineSceneParams.sceneParams
        Filters.userQP(combineSceneParams.userParams) ++
          Filters.timestampQP(combineSceneParams.timestampParams) ++
          List(
            sceneParams.maxCloudCover.map({ mcc =>
              fr"cloud_cover <= $mcc"
            }),
            sceneParams.minCloudCover.map({ mcc =>
              fr"cloud_cover >= $mcc"
            }),
            sceneParams.minAcquisitionDatetime.map({ mac =>
              fr"acquisition_date >= $mac"
            }),
            sceneParams.maxAcquisitionDatetime.map({ mac =>
              fr"acquisition_date <= $mac"
            }),
            sceneParams.datasource.toList.toNel.map({ ds =>
              Fragments.in(fr"datasource", ds)
            }),
            sceneParams.month.toList.toNel.map({ months =>
              Fragments.in(fr"date_part('month', acquisition_date)", months)
            }),
            sceneParams.minDayOfMonth.map({ day =>
              fr"date_part('day', acquisition_date) >= $day"
            }),
            sceneParams.maxDayOfMonth.map({ day =>
              fr"date_part('day', acquisition_date) >= $day"
            }),
            sceneParams.maxSunAzimuth.map({ msa =>
              fr"sun_azimuth <= ${msa}"
            }),
            sceneParams.minSunAzimuth.map({ msa =>
              fr"sun_azimuth >= ${msa}"
            }),
            sceneParams.maxSunElevation.map({ mse =>
              fr"sun_elevation <= ${mse}"
            }),
            sceneParams.minSunElevation.map({ mse =>
              fr"sun_elevation >= ${mse}"
            }),
            sceneParams.ingested.map({
              case true => fr"ingest_status = 'INGESTED'"
              case _    => fr"ingest_status != 'INGESTED'"
            }),
            sceneParams.ingestStatus.toList.toNel.map({ statuses =>
              Fragments
                .in(fr"ingest_status", statuses.map(IngestStatus.fromString(_)))
            }),
            (sceneParams.bboxPolygon, sceneParams.shape) match {
              case (Some(bboxPolygons), _) =>
                val fragments = bboxPolygons.map(
                  bbox =>
                    fr"(_ST_Intersects(data_footprint, ${bbox}) AND tile_footprint && ${bbox})"
                )
                Some(fr"(" ++ Fragments.or(fragments: _*) ++ fr")")
              case _ => None
            }
          )
    }

  implicit val projectSceneQueryParameters
    : Filterable[Any, ProjectSceneQueryParameters] =
    Filterable[Any, ProjectSceneQueryParameters] { params =>
      List(
        params.ingested.map({
          case true => fr"ingest_status = 'INGESTED'"
          case _    => fr"ingest_status != 'INGESTED'"
        }),
        params.ingestStatus.toList.toNel.map({ statuses =>
          Fragments
            .in(fr"ingest_status", statuses.map(IngestStatus.fromString(_)))
        })
      )
    }

  implicit val mapTokenQueryParametersFilter
    : Filterable[Any, CombinedMapTokenQueryParameters] =
    Filterable[Any, CombinedMapTokenQueryParameters] {
      mapTokenParams: CombinedMapTokenQueryParameters =>
        Filters.userQP(mapTokenParams.userParams) ++
          Filters.mapTokenQP(mapTokenParams.mapTokenParams)
    }

  implicit val combinedToolRunQueryParameters
    : Filterable[Any, CombinedToolRunQueryParameters] =
    Filterable[Any, CombinedToolRunQueryParameters] {
      combinedToolRunParams: CombinedToolRunQueryParameters =>
        Filters.userQP(combinedToolRunParams.userParams) ++
          Filters.searchQP(combinedToolRunParams.searchParams, List("name")) ++
          Filters.timestampQP(combinedToolRunParams.timestampParams) ++
          List(
            combinedToolRunParams.toolRunParams.createdBy.map({ createdBy =>
              fr"created_by = ${createdBy}"
            }),
            combinedToolRunParams.toolRunParams.projectId.map({ projectId =>
              fr"project_id = ${projectId}"
            }),
            combinedToolRunParams.toolRunParams.templateId.map({ templateId =>
              fr"template_id = ${templateId}"
            }),
            combinedToolRunParams.toolRunParams.projectLayerId.map({
              projectLayerId =>
                fr"project_layer_id = ${projectLayerId}"
            })
          )
    }

  implicit val fragmentFilter: Filterable[Any, doobie.Fragment] =
    Filterable[Any, Fragment] { fragment: Fragment =>
      List(Some(fragment))
    }

  implicit def maybeTFilter[T](
      implicit filterable: Filterable[Any, T]
  ): Filterable[Any, Option[T]] = Filterable[Any, Option[T]] {
    case None        => List.empty[Option[Fragment]]
    case Some(thing) => filterable.toFilters(thing)
  }

  implicit def listTFilter[T](
      implicit filterable: Filterable[Any, T]
  ): Filterable[Any, List[T]] = Filterable[Any, List[T]] {
    someFilterables: List[T] =>
      {
        someFilterables
          .map(filterable.toFilters)
          .foldLeft(List.empty[Option[Fragment]])(_ ++ _)
      }
  }

  implicit val datasourceQueryparamsFilter
    : Filterable[Any, DatasourceQueryParameters] =
    Filterable[Any, DatasourceQueryParameters] {
      dsParams: DatasourceQueryParameters =>
        Filters.searchQP(dsParams.searchParams, List("name")) ++
          Filters.userQP(dsParams.userParams)
    }

  implicit val uploadQueryParameters: Filterable[Any, UploadQueryParameters] =
    Filterable[Any, UploadQueryParameters] {
      uploadParams: UploadQueryParameters =>
        List(
          uploadParams.datasource.map({ ds =>
            fr"datasource = ${ds}"
          }),
          uploadParams.projectId.map({ pid =>
            fr"project_id = ${pid}"
          }),
          uploadParams.uploadStatus.map({ uploadStatus =>
            val statusF: List[Fragment] = uploadStatus
              .split(",")
              .map(_.trim)
              .toList
              .map(status => fr"upload_status = ${status}::upload_status")
            fr"(" ++ Fragments.or(statusF: _*) ++ fr")"
          }),
          uploadParams.layerId.map({ lid =>
            fr"layer_id = ${lid}"
          })
        )
    }

  implicit val exportQueryparamsFilter: Filterable[Any, ExportQueryParameters] =
    Filterable[Any, ExportQueryParameters] {
      exportParams: ExportQueryParameters =>
        List(
          exportParams.organization.map({ orgId =>
            fr"organization = $orgId"
          }),
          exportParams.project.map({ projId =>
            fr"project_id = $projId"
          }),
          exportParams.analysis.map({ analysisId =>
            fr"toolrun_id = $analysisId"
          }),
          exportParams.layer.map({ layerId =>
            fr"project_layer_id = $layerId"
          }),
          exportParams.exportStatus.toList.toNel.map({ statuses =>
            val exportStatuses = statuses.map({ status =>
              try ExportStatus.fromString(status)
              catch {
                case _: Exception =>
                  throw new IllegalArgumentException(
                    s"Invalid Ingest Status: $status"
                  )
              }
            })
            Fragments.in(fr"export_status", exportStatuses)
          })
        )
    }

  implicit val shapeQueryparamsFilter: Filterable[Any, ShapeQueryParameters] =
    Filterable[Any, ShapeQueryParameters] { shapeParams: ShapeQueryParameters =>
      Filters.timestampQP(shapeParams.timestampParams) ++
        Filters.userQP(shapeParams.userParams) ++
        Filters.searchQP(shapeParams.searchParams, List("name"))

    }

  implicit val thumbnailParamsFilter
    : Filterable[Any, ThumbnailQueryParameters] =
    Filterable[Any, ThumbnailQueryParameters] {
      params: ThumbnailQueryParameters =>
        Filters.thumbnailQP(params)
    }

  implicit val teamQueryparamsFilter: Filterable[Any, TeamQueryParameters] =
    Filterable[Any, TeamQueryParameters] { params: TeamQueryParameters =>
      Filters.timestampQP(params.timestampParams) ++
        Filters.onlyUserQP(params.onlyUserParams) ++
        Filters.searchQP(params.searchParams, List("name")) ++
        Filters.activationQP(params.activationParams)
    }

  implicit val platformQueryparamsFilter
    : Filterable[Any, PlatformQueryParameters] =
    Filterable[Any, PlatformQueryParameters] {
      params: PlatformQueryParameters =>
        Filters.timestampQP(params.timestampParams) ++
          Filters.onlyUserQP(params.onlyUserParams) ++
          Filters.searchQP(params.searchParams, List("name")) ++
          Filters.activationQP(params.activationParams)
    }

  implicit val organizationQueryparamsFilter
    : Filterable[Any, OrganizationQueryParameters] =
    Filterable[Any, OrganizationQueryParameters] {
      params: OrganizationQueryParameters =>
        Filters.timestampQP(params.timestampParams) ++
          Filters.searchQP(params.searchParams, List("name")) ++
          Filters.activationQP(params.activationParams) ++
          Filters.platformIdQP(params.platformIdParams)
    }

  implicit def metricQueryParamsFilter: Filterable[Any, MetricQueryParameters] =
    Filterable[Any, MetricQueryParameters] { params =>
      Filters.metricQP(params)
    }

  implicit val orgSearchQueryParamsFilter
    : Filterable[Organization, SearchQueryParameters] =
    Filterable[Organization, SearchQueryParameters] {
      params: SearchQueryParameters =>
        Filters.searchQP(params, List("name"))
    }

  implicit val userSearchQueryParamsFilter
    : Filterable[User, SearchQueryParameters] =
    Filterable[User, SearchQueryParameters] { params: SearchQueryParameters =>
      Filters.searchQP(params, List("name", "email", "id"))
    }

  implicit def projectedGeometryFilter: Filterable[Any, Projected[Geometry]] =
    Filterable[Any, Projected[Geometry]] { geom =>
      List(Some(fr"ST_Intersects(data_footprint, ${geom})"))
    }

  implicit def projectedMultiPolygonFilter
    : Filterable[Any, Projected[MultiPolygon]] =
    Filterable[Any, Projected[MultiPolygon]] { geom =>
      List(Some(fr"ST_Intersects(data_footprint, ${geom})"))
    }

  implicit def taskQueryParamFilter: Filterable[Any, TaskQueryParameters] =
    Filterable[Any, TaskQueryParameters] { qp =>
      Filters.taskQP(qp)
    }

  implicit val labelStacExportQPFilter
    : Filterable[Any, StacExportQueryParameters] =
    Filterable[Any, StacExportQueryParameters] {
      params: StacExportQueryParameters =>
        Filters.onlyUserQP(params.userParams) ++
          Filters.ownerQP(params.ownerParams) ++
          Filters.searchQP(params.searchParams, List("name")) ++
          List(
            params.exportStatus map { qp =>
              fr"export_status = UPPER($qp)::public.export_status"
            },
            params.annotationProjectId map { qp =>
              fr"annotation_project_id = ${qp}"
            }
          )
    }

  implicit val annotationProjectQueryParametersFilter
    : Filterable[Any, AnnotationProjectQueryParameters] =
    Filterable[Any, AnnotationProjectQueryParameters] {
      params: AnnotationProjectQueryParameters =>
        val taskStatusF = params.projectFilterParams.taskStatusesInclude.toList.toNel map {
          statusList =>
            val statusFilterF = statusList map { status =>
              Some(
                fr"(annotation_projects.task_status_summary ->> ${status.toString}) > '0'")
            }
            Fragment.const("(") ++ Fragments
              .orOpt(statusFilterF.toList: _*) ++ Fragment.const(")")
        }
        Filters.ownerQP(params.ownerParams, fr"annotation_projects.owner") ++
          Filters.searchQP(params.searchParams,
                           List("annotation_projects.name")) ++
          List(
            params.projectFilterParams.projectType.map({ projectType =>
              fr"annotation_projects.project_type = $projectType"
            }),
            taskStatusF
          )
    }
}

object Filterables extends Filterables
