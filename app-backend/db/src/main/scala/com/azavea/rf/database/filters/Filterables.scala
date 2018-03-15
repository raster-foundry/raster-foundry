package com.azavea.rf.database.filter

import java.util.UUID

import com.azavea.rf.database.meta.RFMeta._
import com.azavea.rf.database.Filterable
import com.azavea.rf.datamodel._
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.Fragments.in
import doobie.postgres._
import doobie.postgres.implicits._


trait Filterables {

  implicit val aoiQueryParamsFilter = Filterable[Any, AoiQueryParameters] { qp: AoiQueryParameters =>
      Filters.organizationQP(qp.orgParams) ++
      Filters.userQP(qp.userParams) ++
      Filters.timestampQP(qp.timestampParams)
  }

  implicit val permissionsFilter = Filterable[Any, User] { user: User =>
    val filter =
      if (!user.isInRootOrganization) {
        Some(fr"organization_id = ${user.organizationId}")
      } else {
        None
      }
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
    Filters.organizationQP(projectParams.orgParams) ++
      Filters.timestampQP(projectParams.timestampParams) ++
      Filters.userQP(projectParams.userParams)
  }

  implicit val annotationQueryparamsFilter = Filterable[Any, AnnotationQueryParameters] { annotParams: AnnotationQueryParameters =>
    Filters.organizationQP(annotParams.orgParams) ++
    Filters.userQP(annotParams.userParams) ++ List(
      annotParams.label.map({ label => fr"label = $label" }),
      annotParams.machineGenerated.map({ mg => fr"machine_generated = $mg" }),
      annotParams.minConfidence.map({ minc => fr"min_confidence = $minc" }),
      annotParams.maxConfidence.map({ maxc => fr"max_confidence = $maxc" }),
      annotParams.quality.map({ quality => fr"quality = $quality" })
    )
  }

  implicit val mapTokenQueryParametersFilter = Filterable[Any, CombinedMapTokenQueryParameters] { mapTokenParams: CombinedMapTokenQueryParameters =>
    Filters.organizationQP(mapTokenParams.orgParams) ++
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
      Filters.timestampQP(combinedToolRunParams.timestampParams) ++ List(
        combinedToolRunParams.toolRunParams.createdBy.map({createdBy => fr"created_by = ${createdBy}"}),
        combinedToolRunParams.toolRunParams.projectId.map({projectId => fr"project_id = ${projectId}"}),
        combinedToolRunParams.toolRunParams.toolId.map({toolId => fr"tool_id = ${toolId}"})
      )
    }

  implicit val fragmentFilter = Filterable[Any, Fragment] { fragment: Fragment => List(Some(fragment)) }

  implicit val maybeFragmentFilter = Filterable[Any, Option[Fragment]] { maybeFragment: Option[Fragment] =>
    List(maybeFragment)
  }

  implicit val datasourceQueryparamsFilter = Filterable[Any, DatasourceQueryParameters] { dsParams: DatasourceQueryParameters =>
    List(dsParams.name.map({ name => fr"name = $name" }))
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
    Filters.organizationQP(shapeParams.orgParams) ++
    Filters.timestampQP(shapeParams.timestampParams) ++
    Filters.userQP(shapeParams.userParams)
  }

  implicit val combinedImageQueryparamsFilter = Filterable[Any, CombinedImageQueryParams] { cips: CombinedImageQueryParams =>
    Filters.organizationQP(cips.orgParams) ++
    Filters.timestampQP(cips.timestampParams) ++
    Filters.imageQP(cips.imageParams)
  }
}

object Filterables extends Filterables

