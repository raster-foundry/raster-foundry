import logging
from multiprocessing import Pool
from typing import List, Tuple

from pyproj import Proj, transform
import rasterio
from rasterio.enums import Resampling
from rasterio.features import shapes
from shapely.geometry import shape, MultiPolygon, Polygon
from shapely.ops import cascaded_union

Point = Tuple[float, float]
DOWNSAMPLE_FACTOR = 8.0


logger = logging.getLogger(__name__)


def transform_point_curried(tup: Tuple[Proj, Proj, Point]) -> Point:
    (src_proj, dst_proj, (x, y)) = tup
    return transform(src_proj, dst_proj, x, y)


def transform_point_seq(
    src_proj: Proj, dst_proj: Proj, points: List[Point], pool: Pool
) -> Tuple[float, float]:
    return pool.map(transform_point_curried, [(src_proj, dst_proj, p) for p in points])


def transform_poly(src_proj: Proj, dst_proj: Proj, poly: Polygon) -> Polygon:
    ext_coords = poly.exterior.coords
    int_coords = [x.coords for x in poly.interiors]
    with Pool() as pool:
        return Polygon(
            transform_point_seq(src_proj, dst_proj, ext_coords, pool),
            [
                transform_point_seq(src_proj, dst_proj, interior, pool)
                for interior in int_coords
            ],
        )


def complex_footprint(tif_path: str) -> MultiPolygon:
    with rasterio.open(tif_path, "r") as ds:
        band = ds.read(
            1,
            out_shape=(
                int(ds.height / DOWNSAMPLE_FACTOR),
                int(ds.width / DOWNSAMPLE_FACTOR),
            ),
            resampling=Resampling.bilinear,
        )
    nodata = ds.nodata
    downsampled_transform = ds.transform * ds.transform.scale(DOWNSAMPLE_FACTOR)
    # why not create the projections with ds.crs, ds.crs.data, "EPSG:4326", etc.?
    # for some reason, when we removed the `init=foo` creation, that dramatically changed
    # the projections that we wound up with, which at different times located a tif in Miami in:
    # - the middle of the Atlantic
    # - Antarctica
    # - Guyana
    # - the upper Caribbean
    # That's weird and bad! The WKT string and the Proj4 string together have cooperated, and
    # we no longer have to rely on pyproj to figure out the correct interpretation of our
    # init args.
    # Possibly this error is related to this rasterio commit:
    # https://github.com/mapbox/rasterio/commit/c58d534182844abda7f8003c46601804cc75e2fe
    # Which we upgraded to in
    # https://github.com/raster-foundry/raster-foundry/pull/5557
    src_proj = Proj(ds.crs.to_wkt())
    dst_proj = Proj("+proj=longlat +ellps=WGS84 +datum=WGS84 +no_defs")
    if nodata is not None:
        data_mask = (band != nodata).astype(rasterio.uint8)
    else:
        data_mask = (band > 0).astype(rasterio.uint8)
    polys = shapes(data_mask, transform=downsampled_transform)
    footprint = cascaded_union([shape(x[0]) for x in polys if x[1] == 1]).simplify(0.05)
    # if it has an __iter__ attribute, it's probably a multipolygon, so reproject all
    # the component polygons
    if hasattr(footprint, "__iter__"):
        transformed = [transform_poly(src_proj, dst_proj, p) for p in footprint]
    # otherwise assume it's a simple polygon, and just put its reprojection
    # into a list
    else:
        transformed = [transform_poly(src_proj, dst_proj, footprint)]
    return MultiPolygon(transformed)
