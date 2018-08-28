package com.azavea.rf.tool.maml

import com.azavea.maml.ast._
import geotrellis.raster.CellType

import java.util.UUID

case class CogRaster(sceneId: UUID,
                     band: Option[Int],
                     celltype: Option[CellType],
                     location: String)
    extends Source {
  val kind = MamlKind.Tile
}

case class SceneRaster(sceneId: UUID,
                       band: Option[Int],
                       celltype: Option[CellType],
                       location: String)
    extends Source {
  val kind = MamlKind.Tile
}

case class ProjectRaster(projId: UUID,
                         band: Option[Int],
                         celltype: Option[CellType])
    extends Source {
  val kind = MamlKind.Tile
}
