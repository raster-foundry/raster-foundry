package com.rasterfoundry.batch.stacExport

import com.rasterfoundry.datamodel._
import io.circe.Json

case class ExportData(scenes: List[Scene],
                      scenesGeomExtent: UnionedGeomExtent,
                      tasks: List[Task],
                      taskGeomExtent: UnionedGeomExtent,
                      annotations: Json,
                      labelItemsPropsThin: StacLabelItemPropertiesThin)
