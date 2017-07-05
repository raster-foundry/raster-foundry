package com.azavea.rf

import geotrellis.raster.split._
import geotrellis.raster.{CellSize, MultibandTile}
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.LayerAttributes
import geotrellis.spark.tiling._
import geotrellis.util.Component
import geotrellis.vector._
import geotrellis.spark.io.index.KeyIndex

import org.apache.avro.Schema
import cats._
import cats.data._
import cats.implicits._
import com.github.blemale.scaffeine.{Cache, Scaffeine}
import spray.json._
import spray.json.DefaultJsonProtocol._

import scala.concurrent.duration._
import scala.reflect.ClassTag
import scala.util.Either

package object batch {
  implicit class HasCellSize[A <: { def rows: Int; def cols: Int; def extent: Extent }](obj: A) {
    def cellSize: CellSize = CellSize(obj.extent.width / obj.cols, obj.extent.height / obj.rows)
  }

  implicit class withRasterFoundryTilerKeyMethods(val self: (ProjectedExtent, Int))
      extends TilerKeyMethods[(ProjectedExtent, Int), (SpatialKey, Int)] {
    def extent = self._1.extent
    def translate(spatialKey: SpatialKey) = (spatialKey, self._2)
  }

  implicit val rfSpatialKeyIntComponent =
    Component[(SpatialKey, Int), SpatialKey](from => from._1, (from, to) => (to, from._2))

  implicit val rfProjectedExtentIntComponent =
    Component[(ProjectedExtent, Int), ProjectedExtent](from => from._1, (from, to) => (to, from._2))

  implicit class withMultibandTileSplitMethods(val self: MultibandTile) extends MultibandTileSplitMethods

  /**
    * Custom cache implemented to allow safe multithreading.
    * Can be removed after GeoTrellis 1.2 release.
    * */
  implicit class withAttributeStoreMethods(that: AttributeStore) {
    def cacheReadSafe[T: JsonFormat](layerId: LayerId, attributeName: String)(implicit cache: Cache[(LayerId, String), Any]): T =
      cache.get(layerId -> attributeName, _ => that.read[T](layerId, attributeName)).asInstanceOf[T]

    def readLayerAttributesSafe[H: JsonFormat, M: JsonFormat, K: ClassTag](id: LayerId)(implicit cache: Cache[(LayerId, String), Any]): LayerAttributes[H, M, K] = {
      val blob = cacheReadSafe[JsValue](id, AttributeStore.Fields.metadataBlob).asJsObject
      LayerAttributes(
        blob.fields(AttributeStore.Fields.header).convertTo[H],
        blob.fields(AttributeStore.Fields.metadata).convertTo[M],
        blob.fields(AttributeStore.Fields.keyIndex).convertTo[KeyIndex[K]],
        blob.fields(AttributeStore.Fields.schema).convertTo[Schema]
      )
    }
  }

  /** Borrowed from Cats.
    * TODO: Use /their/ implementation once cats 1.0.0 comes out.
    */
  def fromOptionF[F[_], E, A](fopt: F[Option[A]], ifNone: => E)(implicit F: Functor[F]): EitherT[F, E, A] =
    EitherT(F.map(fopt)(opt => Either.fromOption(opt, ifNone)))

  def retry[A](time: Duration, pause: Duration)(code: => A): A = {
    var result: Option[A] = None
    var remaining = time
    while (remaining > Duration.Zero) {
      remaining -= pause
      try {
        result = Some(code)
        remaining = Duration.Zero
      } catch {
        case _ if remaining > Duration.Zero => Thread.sleep(pause.toMillis)
      }
    }
    result.getOrElse(throw new Exception(s"Retry failed in $time"))
  }
}
