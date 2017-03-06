package com.azavea.rf.tool.ast

import geotrellis.raster.render._
import io.circe._
import io.circe.optics.JsonPath._

import scala.util.Try
import java.security.InvalidParameterException


case class ClassBreaks(boundaryType: ClassBoundaryType, classMap: Map[Double, Double])


