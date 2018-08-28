package com.azavea.rf.tile.tool

import com.azavea.rf.tile._
import com.azavea.rf.tile.image._
import com.azavea.rf.tool.ast._
import com.azavea.rf.tool.eval._
import com.azavea.rf.tool.maml._
import com.azavea.maml.ast._
import com.azavea.maml.eval._
import com.azavea.rf.database.SceneDao
import com.azavea.rf.database.filter.Filterables._
import doobie._
import doobie.implicits._
import com.typesafe.scalalogging.LazyLogging
import cats.data.{NonEmptyList => NEL, _}
import cats.data.Validated._
import cats.implicits._
import geotrellis.raster._
import geotrellis.spark._
import geotrellis.spark.io.s3._
import geotrellis.spark.io._
import geotrellis.vector.Extent
import geotrellis.spark.io.postgres.PostgresAttributeStore

import scala.util._
import scala.concurrent._
import java.net.URI
import java.util.UUID

import cats.effect.IO
import com.azavea.rf.database.util.RFTransactor
import doobie.util.transactor.Transactor

/** Interpreting a [[MapAlgebraAST]] requires providing a function from
  *  (at least) an RFMLRaster (the source/terminal-node type of the AST)
  *  to a Future[Option[Tile]]. This object provides instance of such
  *  functions.
  */
object TileSources extends LazyLogging {

  /** Given the data sources for some AST, determine the "data window" for
    * the entire data set. This ensures that global AST interpretation will behave
    * correctly, so that valid  histograms can be generated.
    */
  implicit val xa = RFTransactor.xa
  val system = AkkaSystem.system
  implicit val blockingDispatcher =
    system.dispatchers.lookup("blocking-dispatcher")
  val store = PostgresAttributeStore()

  def fullDataWindow(
      rs: Set[RFMLRaster]
  )(implicit xa: Transactor[IO]): OptionT[Future, (Extent, Int)] = {
    rs.toStream
      .map(dataWindow)
      .sequence
      .map({ pairs =>
        val (extents, zooms): (Stream[Extent], Stream[Int]) = pairs.unzip
        val extent: Extent = extents.reduce(_ combine _)

        /* The average of all the reported optimal zoom levels. */
        val zoom: Int = zooms.sum / zooms.length

        (extent, zoom)
      })
  }

  /** Given a reference to a source of data, computes the "data window" of
    * the entire dataset. The `Int` is the minimally acceptable zoom level at
    * which one could read a Layer for the purpose of calculating a representative
    * histogram.
    */
  def dataWindow(r: RFMLRaster)(
      implicit xa: Transactor[IO]): OptionT[Future, (Extent, Int)] = r match {
    case MapAlgebraAST.SceneRaster(id, sceneId, Some(_), _, _) => {
      OptionT(Future {
        GlobalSummary.minAcceptableSceneZoom(sceneId, store, 256)
      })
    }
    case MapAlgebraAST.CogRaster(id, sceneId, Some(_), _, _, location) => {
      GlobalSummary.minAcceptableCogZoom(location, 256)
    }
    case MapAlgebraAST.ProjectRaster(id, projId, Some(_), _, _) => {
      OptionT[Future, (Extent, Int)](
        GlobalSummary.minAcceptableProjectZoom(projId, 256).map(Some(_)))
    }

    /* Don't attempt work for a RFMLRaster which will fail AST validation anyway */
    case _ => OptionT.none
  }
}
