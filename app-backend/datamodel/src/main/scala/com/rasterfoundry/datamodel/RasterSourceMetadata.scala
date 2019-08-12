package com.rasterfoundry.datamodel

import java.util.UUID

import geotrellis.contrib.vlm.gdal.GDALDataPath
import geotrellis.proj4.CRS
import geotrellis.raster.{CellSize, CellType, GridExtent}
import geotrellis.vector.Extent

final case class RasterSourceMetadata(id: UUID,
                                      dataPath: GDALDataPath,
                                      crs: CRS,
                                      bandCount: Int,
                                      cellType: CellType,
                                      noDataValue: Option[Double],
                                      gridExtent: GridExtent[Long],
                                      resolutions: List[GridExtent[Long]])
