package com.rasterfoundry.database

import com.rasterfoundry.database.meta.CirceJsonbMeta
import com.rasterfoundry.datamodel._

import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import geotrellis.raster.gdal.GDALPath
import geotrellis.proj4.CRS
import geotrellis.raster.CellType
import geotrellis.vector.Extent

import java.util.UUID

object RasterSourceMetadataDao extends CirceJsonbMeta {

  implicit val dataPathMeta: Meta[GDALPath] =
    Meta[String].timap(GDALPath.apply)(_.value)

  implicit val crsMeta: Meta[CRS] =
    Meta[String].timap(CRS.fromString)(_.toProj4String)

  implicit val cellTypeMeta: Meta[CellType] =
    Meta[String].timap(CellType.fromName)(CellType.toName)

  implicit val extentMeta: Meta[Extent] =
    Meta[Array[Double]].timap { array =>
      Extent(array(0), array(1), array(2), array(3))
    } { e =>
      Array(e.xmin, e.ymin, e.xmax, e.ymax)
    }

  val selectF: Fragment =
    fr"""
    SELECT
      data_path, crs, band_count, cell_type,
      no_data_value, grid_extent, resolutions
    FROM
      scenes
  """

  def select(id: UUID): ConnectionIO[RasterSourceMetadata] = {
    println(s"Getting RS: ${id}")
    (selectF ++ Fragments.whereAnd(fr"id = ${id}"))
      .query[RasterSourceMetadata]
      .unique
  }

  def update(id: UUID, rsm: RasterSourceMetadata): ConnectionIO[Int] = {
    fr"""UPDATE scenes SET
        data_path = ${rsm.dataPath},
        crs = ${rsm.crs},
        band_count = ${rsm.bandCount},
        cell_type = ${rsm.cellType},
        grid_extent = ${rsm.gridExtent},
        resolutions = ${rsm.resolutions},
        no_data_value = ${rsm.noDataValue}
        where id = ${id}
      """.update.run
  }
}
