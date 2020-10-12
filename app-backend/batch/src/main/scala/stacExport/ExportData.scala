package com.rasterfoundry.batch.stacExport

import com.rasterfoundry.datamodel._

import com.azavea.stac4s.extensions.label.LabelItemExtension
import io.circe.Json

case class ExportData(
    scenes: List[Scene],
    scenesGeomExtent: UnionedGeomExtent,
    tasks: List[Task],
    taskGeomExtent: UnionedGeomExtent,
    annotations: Json,
    labelItemExtension: LabelItemExtension,
    taskStatuses: List[TaskStatus]
)
