import logging
from multiprocessing import Pool
from typing import List, Tuple

import numpy as np
from pyproj import Proj, transform
import rasterio
from rasterio.enums import Resampling
from rasterio.features import shapes
from shapely.geometry import shape, MultiPolygon, Polygon
from shapely.ops import cascaded_union

Point = Tuple[float, float]
DOWNSAMPLE_FACTOR = 8.0


logger = logging.getLogger(__name__)


def transform_point_curried(tup: (Proj, Proj, Point)) -> Point:
    (src_proj, dst_proj, (x, y)) = tup
    return transform(src_proj, dst_proj, x, y)


def transform_point_seq(
    src_proj: Proj, dst_proj: Proj, points: List[Point], pool: Pool
) -> (float, float):
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
    downsampled_transform = ds.transform * ds.transform.scale(DOWNSAMPLE_FACTOR)
    src_proj = Proj(ds.crs)
    dst_proj = Proj({"init": "EPSG:4326"})
    data_mask = (band > 0).astype(np.uint8)
    polys = shapes(data_mask, transform=downsampled_transform)
    multipoly = cascaded_union([shape(x[0]) for x in polys if x[1] == 1])
    transformed = [
        transform_poly(src_proj, dst_proj, p) for p in multipoly.simplify(0.05)
    ]
    return MultiPolygon(transformed)
