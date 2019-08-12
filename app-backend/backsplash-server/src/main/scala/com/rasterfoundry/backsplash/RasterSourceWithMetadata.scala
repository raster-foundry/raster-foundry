package com.rasterfoundry.backsplash

import cats.Semigroup
import cats.implicits._
import cats.data.NonEmptyList
import geotrellis.vector._
import geotrellis.raster._
import geotrellis.raster.merge.Implicits._
import geotrellis.raster.resample._
import geotrellis.raster.reproject.Reproject
import geotrellis.proj4.{CRS, WebMercator}
import geotrellis.raster.io.geotiff.{AutoHigherResolution, OverviewStrategy}
import geotrellis.raster.render._
import geotrellis.spark.tiling.LayoutDefinition
import geotrellis.util.GetComponent
import spire.math.Integral

import geotrellis.contrib.vlm.{
  DataPath,
  RasterSource,
  ResampleGrid,
  TargetCellType
}
import geotrellis.contrib.vlm.Implicits._
import geotrellis.proj4.CRS
import geotrellis.raster.io.geotiff.OverviewStrategy
import geotrellis.raster.{
  CellType,
  GridBounds,
  GridExtent,
  MultibandTile,
  Raster
}
import geotrellis.raster.reproject.Reproject
import geotrellis.raster.resample.ResampleMethod
import geotrellis.vector.Extent
import com.rasterfoundry.datamodel.RasterSourceMetadata
import geotrellis.contrib.vlm.gdal.{
  GDALDataset,
  GDALRasterSource,
  GDALWarpOptions
}

case class RasterSourceWithMetadata(rsm: RasterSourceMetadata,
                                    rasterSource: GDALRasterSource,
                                    targetCellType: Option[TargetCellType] =
                                      None)
    extends RasterSource {
  println(s"Created RS")

  val dataPath = rasterSource.dataPath

  val path: String = dataPath.path

  override def crs: CRS = rsm.crs

  override def bandCount: Int = rsm.bandCount

  override def cellType: CellType = rsm.cellType

  override def gridExtent: GridExtent[Long] = {
    rsm.gridExtent
  }

  override def resolutions: List[GridExtent[Long]] = rsm.resolutions

  override def reproject(targetCRS: CRS,
                         reprojectOptions: Reproject.Options,
                         strategy: OverviewStrategy): RasterSource = {
    val reprojectedGridExtent =
      this.gridExtent.reproject(this.crs, targetCRS, reprojectOptions)

    println(s"""Source: ${this.crs.toProj4String}, ${this.gridExtent} ...
         |Target: ${targetCRS.toProj4String}, ${reprojectedGridExtent}""".stripMargin)

    RasterSourceWithMetadata(
      rsm.copy(crs = targetCRS, gridExtent = reprojectedGridExtent),
      GDALRasterSource(dataPath,
                       rasterSource.options.reproject(gridExtent,
                                                      crs,
                                                      targetCRS,
                                                      reprojectOptions))
    )
  }

  override def resample(resampleGrid: ResampleGrid[Long],
                        method: ResampleMethod,
                        strategy: OverviewStrategy): RasterSource = {
    RasterSourceWithMetadata(
      rsm.copy(gridExtent = resampleGrid(gridExtent)),
      GDALRasterSource(dataPath,
                       rasterSource.options.resample(gridExtent, resampleGrid))
    )
  }

  override def read(extent: Extent,
                    bands: Seq[Int]): Option[Raster[MultibandTile]] = {
    require(rsm.crs == rasterSource.crs)
//    require(rsm.gridExtent == rasterSource.gridExtent)
    rasterSource.read(extent, bands)
  }

  override def read(bounds: GridBounds[Long],
                    bands: Seq[Int]): Option[Raster[MultibandTile]] = {
    require(rsm.crs == rasterSource.crs)
//    require(rsm.gridExtent == rasterSource.gridExtent)
    rasterSource.read(bounds, bands)
  }

  override def readBounds(bounds: Traversable[GridBounds[Long]],
                          bands: Seq[Int]): Iterator[Raster[MultibandTile]] = {
    require(rsm.crs == rasterSource.crs)
//    require(rsm.gridExtent == rasterSource.gridExtent)
    rasterSource.readBounds(bounds, bands)

  }

  override def convert(targetCellType: TargetCellType): RasterSource = {
    RasterSourceWithMetadata(
      rsm.copy(cellType = targetCellType.cellType),
      GDALRasterSource(
        dataPath,
        rasterSource.options.convert(targetCellType,
                                     rsm.noDataValue,
                                     Some(cols.toInt -> rows.toInt)),
        Some(targetCellType))
    )
  }
}

/**
  * Single threaded instance of a reader for reading windows out of collections
  * of rasters
  *
  * @param sources The underlying [[RasterSource]]s that you'll use for data access
  * @param crs The crs to reproject all [[RasterSource]]s to anytime we need information about their data
  * Since MosaicRasterSources represent collections of [[RasterSource]]s, we don't know in advance
  * whether they'll have the same CRS. crs allows specifying the CRS on read instead of
  * having to make sure at compile time that you're threading CRSes through everywhere correctly.
  */
trait RFMosaicRasterSource extends RasterSource {

  val sources: NonEmptyList[RasterSource]
  val crs: CRS
  def gridExtent: GridExtent[Long]

  import RFMosaicRasterSource._

  /**
    * Uri is required only for compatibility with RasterSource.
    *
    * It doesn't make sense to access "the" URI for a collection, so this throws an exception.
    */
  def dataPath: DataPath = throw new NotImplementedError(
    """
      | MosaicRasterSources don't have a single dataPath. Perhaps you wanted the dataPath from
      | one of this MosaicRasterSource's sources?
    """.trim.stripMargin
  )

  val targetCellType = None

  /**
    * The bandCount of the first [[RasterSource]] in sources
    *
    * If this value is larger than the bandCount of later [[RasterSource]]s in sources,
    * reads of all bands will fail. It is a client's responsibility to construct
    * mosaics that can be read.
    */
  def bandCount: Int = sources.head.bandCount

  def cellType: CellType = {
    val cellTypes = sources map { _.cellType }
    cellTypes.tail.foldLeft(cellTypes.head)(_ union _)
  }

  /**
    * All available resolutions for all RasterSources in this MosaicRasterSource
    *
    * @see [[geotrellis.contrib.vlm.RasterSource.resolutions]]
    */
  def resolutions: List[GridExtent[Long]] = {
    val resolutions = sources map { _.resolutions }
    resolutions.reduce
  }

  /** Create a new MosaicRasterSource with sources transformed according to the provided
    * crs, options, and strategy, and a new crs
    *
    * @see [[geotrellis.contrib.vlm.RasterSource.reproject]]
    */
  def reproject(crs: CRS,
                reprojectOptions: Reproject.Options,
                strategy: OverviewStrategy): RasterSource =
    RFMosaicRasterSource(
      sources map { _.reproject(crs, reprojectOptions, strategy) },
      crs,
      gridExtent.reproject(this.crs, crs, reprojectOptions)
    )

  def read(extent: Extent, bands: Seq[Int]): Option[Raster[MultibandTile]] = {
    val rasters = sources.filter(_.extent.intersects(extent)).map {
      _.read(extent, bands)
    }

    rasters.toNel match {
      case Some(r) => r.reduce
      case _       => None
    }
  }

  def read(bounds: GridBounds[Long],
           bands: Seq[Int]): Option[Raster[MultibandTile]] = {
    val extent = gridExtent.extentFor(bounds)
    read(extent, bands)
  }

  def resample(resampleGrid: ResampleGrid[Long],
               method: ResampleMethod,
               strategy: OverviewStrategy): RasterSource = RFMosaicRasterSource(
    sources map { _.resample(resampleGrid, method, strategy) },
    crs
  )

  def convert(targetCellType: TargetCellType): RasterSource = {
    RFMosaicRasterSource(
      sources map { _.convert(targetCellType) },
      crs
    )
  }
}

object RFMosaicRasterSource {
  // Orphan instance for semigroups for rasters, so we can combine
  // Option[Raster[_]]s later
  implicit val rasterSemigroup: Semigroup[Raster[MultibandTile]] =
    new Semigroup[Raster[MultibandTile]] {
      def combine(l: Raster[MultibandTile], r: Raster[MultibandTile]) = {
        val targetRE = RasterExtent(
          l.rasterExtent.extent combine r.rasterExtent.extent,
          List(l.rasterExtent.cellSize, r.rasterExtent.cellSize)
            .minBy(_.resolution))
        val result = l.resample(targetRE) merge r.resample(targetRE)
        result
      }
    }

  implicit def gridExtentSemigroup[N: Integral]: Semigroup[GridExtent[N]] =
    new Semigroup[GridExtent[N]] {
      def combine(l: GridExtent[N], r: GridExtent[N]): GridExtent[N] = {
        if (l.cellwidth != r.cellwidth)
          throw GeoAttrsError(
            s"illegal cellwidths: ${l.cellwidth} and ${r.cellwidth}")
        if (l.cellheight != r.cellheight)
          throw GeoAttrsError(
            s"illegal cellheights: ${l.cellheight} and ${r.cellheight}")

        val newExtent = l.extent.combine(r.extent)
        val newRows =
          Integral[N].fromDouble(math.round(newExtent.height / l.cellheight))
        val newCols =
          Integral[N].fromDouble(math.round(newExtent.width / l.cellwidth))
        new GridExtent[N](newExtent,
                          l.cellwidth,
                          l.cellheight,
                          newCols,
                          newRows)
      }
    }

  def apply(_sources: NonEmptyList[RasterSource],
            _crs: CRS,
            _gridExtent: GridExtent[Long]) =
    new RFMosaicRasterSource {
      val sources = _sources map { _.reprojectToGrid(_crs, gridExtent) }
      val crs = _crs
      def gridExtent: GridExtent[Long] = _gridExtent
    }

  def apply(_sources: NonEmptyList[RasterSource], _crs: CRS) =
    new RFMosaicRasterSource {
      val sources = _sources map {
        _.reprojectToGrid(_crs, _sources.head.gridExtent)
      }
      val crs = _crs
      def gridExtent: GridExtent[Long] = {
        val reprojectedExtents =
          _sources map { source =>
            source.gridExtent.reproject(source.crs, _crs)
          }
        val minCellSize: CellSize = reprojectedExtents.toList map {
          rasterExtent =>
            CellSize(rasterExtent.cellwidth, rasterExtent.cellheight)
        } minBy { _.resolution }
        reprojectedExtents.toList.reduce(
          (re1: GridExtent[Long], re2: GridExtent[Long]) => {
            re1.withResolution(minCellSize) combine re2.withResolution(
              minCellSize)
          }
        )
      }
    }

  @SuppressWarnings(Array("TraversableHead", "TraversableTail"))
  def unsafeFromList(_sources: List[RasterSource],
                     _crs: CRS = WebMercator,
                     _gridExtent: Option[GridExtent[Long]]) =
    new RFMosaicRasterSource {
      val sources = NonEmptyList(_sources.head, _sources.tail)
      val crs = _crs
      def gridExtent: GridExtent[Long] = _gridExtent getOrElse {
        _sources.head.gridExtent
      }
    }
}
