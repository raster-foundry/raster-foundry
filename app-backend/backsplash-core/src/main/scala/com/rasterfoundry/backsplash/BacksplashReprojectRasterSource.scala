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
import geotrellis.raster.reproject._
import geotrellis.raster.resample._
import geotrellis.vector._

/** Custom RasterSource that allows us to pass in cached [[GeoTiffInfo]]
  * rather than incur the overhead of reading the info for every reads/raster
  * source creation
  *
  * There is a lot of copy/paste here because we can't extend the RasterSource
  * case class without a bunch of trouble
  */
case class BacksplashReprojectRasterSource(
    info: GeoTiffInfo,
    dataPath: GeoTiffDataPath,
    crs: CRS,
    reprojectOptions: Reproject.Options = Reproject.Options.DEFAULT,
    strategy: OverviewStrategy = AutoHigherResolution,
    targetCellType: Option[TargetCellType] = None
) extends RasterSource { self =>
  def resampleMethod: Option[ResampleMethod] = Some(reprojectOptions.method)

  @transient lazy val tiff: MultibandGeoTiff =
    BacksplashGeotiffReader.readMultibandWithInfo(info)

  protected lazy val baseCRS: CRS = tiff.crs
  protected lazy val baseGridExtent: GridExtent[Long] =
    tiff.rasterExtent.toGridType[Long]

  protected lazy val transform = Transform(baseCRS, crs)
  protected lazy val backTransform = Transform(crs, baseCRS)

  override lazy val gridExtent: GridExtent[Long] =
    reprojectOptions.targetRasterExtent match {
      case Some(targetRasterExtent) =>
        targetRasterExtent.toGridType[Long]

      case None =>
        ReprojectRasterExtent(baseGridExtent, transform, reprojectOptions)
    }

  lazy val resolutions: List[GridExtent[Long]] =
    gridExtent :: tiff.overviews.map(ovr =>
      ReprojectRasterExtent(ovr.rasterExtent.toGridType[Long], transform))

  @transient lazy val closestTiffOverview: GeoTiff[MultibandTile] = {
    if (reprojectOptions.targetRasterExtent.isDefined
        || reprojectOptions.parentGridExtent.isDefined
        || reprojectOptions.targetCellSize.isDefined) {
      // we're asked to match specific target resolution, estimate what resolution we need in source to sample it
      val estimatedSource = ReprojectRasterExtent(gridExtent, backTransform)
      tiff.getClosestOverview(estimatedSource.cellSize, strategy)
    } else {
      tiff.getClosestOverview(baseGridExtent.cellSize, strategy)
    }
  }

  def bandCount: Int = tiff.bandCount
  def cellType: CellType = dstCellType.getOrElse(tiff.cellType)

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
    val bounds = extents.map(gridExtent.gridBoundsFor(_, clamp = true))
    readBounds(bounds, bands)
  }

  @SuppressWarnings(Array("AsInstanceOf"))
  override def readBounds(bounds: Traversable[GridBounds[Long]],
                          bands: Seq[Int]): Iterator[Raster[MultibandTile]] = {
    val geoTiffTile =
      closestTiffOverview.tile.asInstanceOf[GeoTiffMultibandTile]
    val intersectingWindows = {
      for {
        queryPixelBounds <- bounds
        targetPixelBounds <- queryPixelBounds.intersection(this.gridBounds)
      } yield {
        val targetRasterExtent = RasterExtent(
          extent = gridExtent.extentFor(targetPixelBounds, clamp = true),
          cols = targetPixelBounds.width.toInt,
          rows = targetPixelBounds.height.toInt)
        val sourceExtent = targetRasterExtent.extent
          .reprojectAsPolygon(backTransform, 0.001)
          .envelope
        val sourcePixelBounds = closestTiffOverview.rasterExtent.gridBoundsFor(
          sourceExtent,
          clamp = true)
        (sourcePixelBounds, targetRasterExtent)
      }
    }.toMap

    geoTiffTile
      .crop(intersectingWindows.keys.toSeq, bands.toArray)
      .map {
        case (sourcePixelBounds, tile) =>
          val targetRasterExtent = intersectingWindows(sourcePixelBounds)
          val sourceRaster =
            Raster(tile,
                   closestTiffOverview.rasterExtent.extentFor(sourcePixelBounds,
                                                              clamp = true))
          val rr = implicitly[RasterRegionReproject[MultibandTile]]
          rr.regionReproject(
            sourceRaster,
            baseCRS,
            crs,
            targetRasterExtent,
            targetRasterExtent.extent.toPolygon,
            reprojectOptions.method,
            reprojectOptions.errorThreshold
          )
      }
      .map { convertRaster }
  }

  def reproject(targetCRS: CRS,
                reprojectOptions: Reproject.Options,
                strategy: OverviewStrategy): RasterSource =
    BacksplashReprojectRasterSource(info,
                                    dataPath,
                                    targetCRS,
                                    reprojectOptions,
                                    strategy,
                                    targetCellType)

  def resample(resampleGrid: ResampleGrid[Long],
               method: ResampleMethod,
               strategy: OverviewStrategy): RasterSource =
    BacksplashReprojectRasterSource(
      info,
      dataPath,
      crs,
      reprojectOptions.copy(method = method,
                            targetRasterExtent = Some(
                              resampleGrid(self.gridExtent).toRasterExtent)),
      strategy,
      targetCellType)

  def convert(targetCellType: TargetCellType): RasterSource =
    BacksplashReprojectRasterSource(info,
                                    dataPath,
                                    crs,
                                    reprojectOptions,
                                    strategy,
                                    Some(targetCellType))
}
