package com.azavea.rf.tool.ast

import geotrellis.raster.render._


case class ClassBreaks(classMap: Map[Double, Double], boundaryType: ClassBoundaryType = LessThanOrEqualTo)

