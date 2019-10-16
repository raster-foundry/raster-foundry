package com.rasterfoundry.backsplash

import geotrellis.contrib.vlm._
import geotrellis.contrib.vlm.geotiff.GeoTiffDataPath
import geotrellis.proj4._
import geotrellis.raster._
import geotrellis.raster.io.geotiff.reader.GeoTiffReader.GeoTiffInfo
import geotrellis.raster.io.geotiff.{
  AutoHigherResolution,
  GeoTiff,
  GeoTiffMultibandTile,
  MultibandGeoTiff,
  OverviewStrategy
}
import geotrellis.raster.reproject.{Reproject, ReprojectRasterExtent}
import geotrellis.raster.resample.{NearestNeighbor, ResampleMethod}
import geotrellis.vector._

/** Custom RasterSource that allows us to pass in cached [[GeoTiffInfo]]
  * rather than incur the overhead of reading the info for every reads/raster
  * source creation
  *
  * There is a lot of copy/paste here because we can't extend the RasterSource
  * case class without a bunch of trouble
  */
case class BacksplashResampleRasterSource(
    info: GeoTiffInfo,
    dataPath: GeoTiffDataPath,
    resampleGrid: ResampleGrid[Long],
    method: ResampleMethod = NearestNeighbor,
    strategy: OverviewStrategy = AutoHigherResolution,
    targetCellType: Option[TargetCellType] = None
) extends RasterSource { self =>
  def resampleMethod: Option[ResampleMethod] = Some(method)

  @transient lazy val tiff: MultibandGeoTiff =
    BacksplashGeotiffReader.readMultibandWithInfo(info)

  def crs: CRS = tiff.crs
  def bandCount: Int = tiff.bandCount
  def cellType: CellType = dstCellType.getOrElse(tiff.cellType)

  override lazy val gridExtent: GridExtent[Long] = resampleGrid(
    tiff.rasterExtent.toGridType[Long])
  lazy val resolutions: List[GridExtent[Long]] = {
    val ratio = gridExtent.cellSize.resolution / tiff.rasterExtent.cellSize.resolution
    gridExtent :: tiff.overviews.map { ovr =>
      val re = ovr.rasterExtent
      val CellSize(cw, ch) = re.cellSize
      new GridExtent[Long](re.extent, CellSize(cw * ratio, ch * ratio))
    }
  }

  @transient protected lazy val closestTiffOverview: GeoTiff[MultibandTile] =
    tiff.getClosestOverview(gridExtent.cellSize, strategy)

  def reproject(targetCRS: CRS,
                reprojectOptions: Reproject.Options,
                strategy: OverviewStrategy): BacksplashReprojectRasterSource =
    new BacksplashReprojectRasterSource(info,
                                        dataPath,
                                        targetCRS,
                                        reprojectOptions,
                                        strategy,
                                        targetCellType) {
      override lazy val gridExtent: GridExtent[Long] =
        reprojectOptions.targetRasterExtent match {
          case Some(targetRasterExtent) => targetRasterExtent.toGridType[Long]
          case None =>
            ReprojectRasterExtent(self.gridExtent,
                                  this.transform,
                                  this.reprojectOptions)
        }
    }

  def resample(resampleGrid: ResampleGrid[Long],
               method: ResampleMethod,
               strategy: OverviewStrategy): RasterSource =
    BacksplashResampleRasterSource(info,
                                   dataPath,
                                   resampleGrid,
                                   method,
                                   strategy,
                                   targetCellType)

  def convert(targetCellType: TargetCellType): RasterSource =
    BacksplashResampleRasterSource(info,
                                   dataPath,
                                   resampleGrid,
                                   method,
                                   strategy,
                                   Some(targetCellType))

  def read(extent: Extent, bands: Seq[Int]): Option[Raster[MultibandTile]] = {
    val bounds = gridExtent.gridBoundsFor(extent, clamp = false)
    val it = readBounds(List(bounds), bands)
    if (it.hasNext) Some(it.next) else None
  }

  def read(bounds: GridBounds[Long],
           bands: Seq[Int]): Option[Raster[MultibandTile]] = {
    val it = readBounds(List(bounds), bands)
    if (it.hasNext) Some(it.next) else None
  }

  override def readExtents(extents: Traversable[Extent],
                           bands: Seq[Int]): Iterator[Raster[MultibandTile]] = {
    val targetPixelBounds = extents.map(gridExtent.gridBoundsFor(_))
    // result extents may actually expand to cover pixels at our resolution
    // TODO: verify the logic here, should the sourcePixelBounds be calculated from input or expanded extent?
    readBounds(targetPixelBounds, bands)
  }

  @SuppressWarnings(Array("AsInstanceOf"))
  override def readBounds(bounds: Traversable[GridBounds[Long]],
                          bands: Seq[Int]): Iterator[Raster[MultibandTile]] = {
    val geoTiffTile =
      closestTiffOverview.tile.asInstanceOf[GeoTiffMultibandTile]

    val windows = {
      for {
        queryPixelBounds <- bounds
        targetPixelBounds <- queryPixelBounds.intersection(this.gridBounds)
      } yield {
        val targetExtent = gridExtent.extentFor(targetPixelBounds)
        val sourcePixelBounds = closestTiffOverview.rasterExtent.gridBoundsFor(
          targetExtent,
          clamp = true)
        val targetRasterExtent = RasterExtent(targetExtent,
                                              targetPixelBounds.width.toInt,
                                              targetPixelBounds.height.toInt)
        (sourcePixelBounds, targetRasterExtent)
      }
    }.toMap

    geoTiffTile.crop(windows.keys.toSeq, bands.toArray).map {
      case (gb, tile) =>
        val targetRasterExtent = windows(gb)
        Raster(
          tile = tile,
          extent = targetRasterExtent.extent
        ).resample(targetRasterExtent.cols, targetRasterExtent.rows, method)
    }
  }
}
