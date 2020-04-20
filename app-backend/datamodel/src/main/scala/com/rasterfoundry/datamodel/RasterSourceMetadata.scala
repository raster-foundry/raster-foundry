package com.rasterfoundry.datamodel

import geotrellis.proj4.CRS
import geotrellis.raster.gdal.GDALPath
import geotrellis.raster.{CellType, GridExtent}

/** Metadata used in the RasterSourceWithMetadata source
  * so that the information supplied here can be used instead
  * of reading metadata from the source
  *
  * @param dataPath
  * @param crs
  * @param bandCount
  * @param cellType
  * @param noDataValue
  * @param gridExtent
  * @param resolutions
  */
final case class RasterSourceMetadata(dataPath: GDALPath,
                                      crs: CRS,
                                      bandCount: Int,
                                      cellType: CellType,
                                      noDataValue: Option[Double],
                                      gridExtent: GridExtent[Long],
                                      resolutions: List[GridExtent[Long]])
