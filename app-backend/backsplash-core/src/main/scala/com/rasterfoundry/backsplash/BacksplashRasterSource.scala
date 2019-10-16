package com.rasterfoundry.backsplash

import geotrellis.contrib.vlm._
import geotrellis.contrib.vlm.geotiff.GeoTiffDataPath
import geotrellis.proj4._
import geotrellis.raster._
import geotrellis.raster.io.geotiff.reader.GeoTiffReader.GeoTiffInfo
import geotrellis.raster.io.geotiff.{
  GeoTiffMultibandTile,
  MultibandGeoTiff,
  OverviewStrategy
}
import geotrellis.raster.reproject.Reproject
import geotrellis.raster.resample.ResampleMethod
import geotrellis.vector._

/** Custom RasterSource that allows us to pass in cached [[GeoTiffInfo]]
  * rather than incur the overhead of reading the info for every reads/raster
  * source creation
  *
  * There is a lot of copy/paste here because we can't extend the RasterSource
  * case class without a bunch of trouble
  *
  * @param info
  * @param dataPath
  * @param targetCellType
  */
case class BacksplashRasterSource(info: GeoTiffInfo,
                                  dataPath: GeoTiffDataPath,
                                  targetCellType: Option[TargetCellType] = None)
    extends RasterSource {
  def resampleMethod: Option[ResampleMethod] = None

  @transient lazy val tiff: MultibandGeoTiff =
    BacksplashGeotiffReader.readMultibandWithInfo(info)

  lazy val gridExtent: GridExtent[Long] = tiff.rasterExtent.toGridType[Long]
  lazy val resolutions: List[GridExtent[Long]] = gridExtent :: tiff.overviews
    .map(_.rasterExtent.toGridType[Long])

  def crs: CRS = tiff.crs
  def bandCount: Int = tiff.bandCount
  def cellType: CellType = dstCellType.getOrElse(tiff.cellType)

  def reproject(targetCRS: CRS,
                reprojectOptions: Reproject.Options,
                strategy: OverviewStrategy): BacksplashReprojectRasterSource =
    BacksplashReprojectRasterSource(info,
                                    dataPath,
                                    targetCRS,
                                    reprojectOptions,
                                    strategy,
                                    targetCellType)

  def resample(resampleGrid: ResampleGrid[Long],
               method: ResampleMethod,
               strategy: OverviewStrategy): BacksplashResampleRasterSource =
    BacksplashResampleRasterSource(info,
                                   dataPath,
                                   resampleGrid,
                                   method,
                                   strategy,
                                   targetCellType)

  def convert(targetCellType: TargetCellType): BacksplashRasterSource =
    BacksplashRasterSource(info, dataPath, Some(targetCellType))

  @SuppressWarnings(Array("AsInstanceOf"))
  def read(extent: Extent, bands: Seq[Int]): Option[Raster[MultibandTile]] = {
    val bounds = gridExtent.gridBoundsFor(extent, clamp = false).toGridType[Int]
    val geoTiffTile = tiff.tile.asInstanceOf[GeoTiffMultibandTile]
    val it = geoTiffTile.crop(List(bounds), bands.toArray).map {
      case (gb, tile) =>
        // TODO: shouldn't GridExtent give me Extent for types other than N ?
        Raster(tile, gridExtent.extentFor(gb.toGridType[Long], clamp = false))
    }
    if (it.hasNext) Some(convertRaster(it.next)) else None
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
    val geoTiffTile = tiff.tile.asInstanceOf[GeoTiffMultibandTile]
    val intersectingBounds: Seq[GridBounds[Int]] =
      bounds
        .flatMap(_.intersection(this.gridBounds))
        .toSeq
        .map(b => b.toGridType[Int])

    geoTiffTile.crop(intersectingBounds, bands.toArray).map {
      case (gb, tile) =>
        convertRaster(
          Raster(tile, gridExtent.extentFor(gb.toGridType[Long], clamp = true)))
    }
  }
}
