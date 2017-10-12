package com.azavea.rf.batch

import com.azavea.rf.tool.ast._
import com.azavea.rf.tool.eval._
import com.azavea.rf.tool.maml.{ProjectRaster => PrRaster, SceneRaster => ScRaster, _}
import com.azavea.rf.tool.ast.MapAlgebraAST._

import com.azavea.maml.spark.eval._
import com.azavea.maml.eval._
import cats.data.NonEmptyList
import cats.data.Validated._
import cats.implicits._
import geotrellis.raster._
import geotrellis.raster.mapalgebra.local.{Pow => GTPow, Xor => GTXor, Or => GTOr, And => GTAnd}
import geotrellis.raster.mapalgebra.local.{Less => GTLess, LessOrEqual => GTLessOrEqual}
import geotrellis.raster.mapalgebra.local.{Equal => GTEqual, Unequal => GTUnequal}
import geotrellis.raster.mapalgebra.local.{Greater => GTGreater, GreaterOrEqual => GTGreaterOrEqual}
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.s3._
import geotrellis.spark.render._
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

import java.util.UUID


package object ast {

  /** Evaluate an AST of RDD Sources. Assumes that the AST's
    * [[NodeMetadata]] has already been replaced, if applicable.
    */
  def interpretRDD(
    ast: MapAlgebraAST,
    zoom: Int,
    sceneLocs: Map[UUID, String],
    projLocs: Map[UUID, List[(UUID, String)]]
  )(implicit sc: SparkContext): Interpreted[TileLayerRDD[SpatialKey]] = {

    /* Guarantee correctness before performing Map Algebra */
    RfmlRddResolver.resolveRdd(ast.asMaml._1, zoom, sceneLocs, projLocs)
      .andThen(RDDInterpreter.DEFAULT(_))
      .andThen(_.as[ContextRDD[SpatialKey, Tile, TileLayerMetadata[SpatialKey]]])
  }
}
