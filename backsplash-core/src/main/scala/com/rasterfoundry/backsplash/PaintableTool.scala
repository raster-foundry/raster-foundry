package com.rasterfoundry.backsplash

import com.azavea.maml.ast.Expression
import com.azavea.maml.eval.BufferingInterpreter
import geotrellis.raster.MultibandTile
import geotrellis.server.{TmsReification, ExtentReification, HasRasterExtents}

case class PaintableTool[Param: TmsReification: ExtentReification: HasRasterExtents](
    expr: Expression,
    painter: MultibandTile => MultibandTile,
    paramMap: Map[String, Param],
    interpreter: BufferingInterpreter = BufferingInterpreter.DEFAULT,
    paint: Boolean // whether to paint the tile or return raw values
)
