package com.rasterfoundry.batch.stacExport

import com.rasterfoundry.datamodel._

import com.azavea.stac4s.extensions.label.LabelItemExtension
import io.circe.Json

import java.util.UUID

case class ExportData(
    annotationProjectName: String,
    scenes: List[Scene],
    scenesGeomExtent: Option[UnionedGeomExtent],
    tasks: List[Task],
    taskGeomExtent: UnionedGeomExtent,
    annotations: Json,
    labelItemExtension: LabelItemExtension,
    taskStatuses: List[TaskStatus],
    projectLayerId: Option[UUID],
    tileLayers: List[TileLayer]
)
