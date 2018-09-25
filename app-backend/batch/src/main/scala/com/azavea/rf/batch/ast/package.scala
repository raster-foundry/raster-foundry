package com.azavea.rf.batch

import java.util.UUID

import cats.effect.IO
import com.azavea.maml.eval._
import com.azavea.maml.spark.eval._
import com.azavea.rf.tool.ast._
import com.azavea.rf.tool.maml._
import doobie.Transactor
import geotrellis.raster._
import geotrellis.spark._
import org.apache.spark.SparkContext

package object ast {

  /** Evaluate an AST of RDD Sources. Assumes that the AST's
    * [[NodeMetadata]] has already been replaced, if applicable.
    */
  def interpretRDD(
      ast: MapAlgebraAST,
      zoom: Int,
      projLocs: Map[UUID, List[(UUID, String)]]
  )(implicit sc: SparkContext,
    xa: Transactor[IO]): IO[Interpreted[TileLayerRDD[SpatialKey]]] = {

    /* Guarantee correctness before performing Map Algebra */
    RfmlRddResolver.resolveRdd(ast.asMaml._1, zoom, projLocs) map {
      _.andThen(RDDInterpreter.DEFAULT(_))
        .andThen(
          _.as[ContextRDD[SpatialKey, Tile, TileLayerMetadata[SpatialKey]]])
    }
  }
}
