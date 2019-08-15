package com.rasterfoundry.batch.stacExport

import java.util.UUID
import io.circe._

import com.rasterfoundry.datamodel._

case class ContentBundle(
    export: StacExport,
    layerToSceneTaskAnnotation: Map[UUID,
                                    (List[Scene],
                                     Option[UnionedGeomExtent],
                                     List[Task],
                                     Option[UnionedGeomExtent],
                                     Option[Json],
                                     Option[StacLabelItemPropertiesThin])]
)
