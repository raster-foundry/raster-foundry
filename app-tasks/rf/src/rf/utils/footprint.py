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


def transform_point_curried(tup: (Proj, Proj, Point)) -> Point:
    (src_proj, dst_proj, (x, y)) = tup
    return transform(src_proj, dst_proj, x, y)


def transform_point_seq(
    src_proj: Proj, dst_proj: Proj, points: List[Point]
) -> (float, float):
    with Pool() as pool:
        return pool.map(
            transform_point_curried, [(src_proj, dst_proj, p) for p in points]
        )


def transform_poly(src_proj: Proj, dst_proj: Proj, poly: Polygon) -> Polygon:
    ext_coords = poly.exterior.coords
    int_coords = [x.coords for x in poly.interiors]
    return Polygon(
        transform_point_seq(src_proj, dst_proj, ext_coords),
        [transform_point_seq(src_proj, dst_proj, interior) for interior in int_coords],
    )


def complex_footprint(tif_path: str) -> MultiPolygon:
    with rasterio.open(tif_path, "r") as ds:
        band = ds.read(
            1,
            out_shape=(int(ds.height / 8.0), int(ds.width / 8.0)),
            resampling=Resampling.bilinear,
        )
    print("band is read")
    src_proj = Proj(ds.crs["init"])
    dst_proj = Proj("EPSG:4326")
    print("calculating shapes")
    data_mask = (band > 0).astype(np.uint8)
    polys = shapes(data_mask, transform=ds.transform)
    print("shapes calculated, doing big ol' union")
    multipoly = cascaded_union([shape(x[0]) for x in polys if x[1] == 1])
    print("transforming polygons")
    transformed = [
        transform_poly(src_proj, dst_proj, p) for p in multipoly.simplify(0.05)
    ]
    print("transform complete, constructing multipoly")
    return MultiPolygon(transformed)
