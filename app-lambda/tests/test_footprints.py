from shapely.geometry import MultiPolygon, Polygon, shape

from rflambda.fields import Footprints


def test_footprint_shift():
    bbox = [(179, 1), (-179, 1), (-179, 2), (179, 2)]
    bad_poly = MultiPolygon(
        [Polygon(bbox + [bbox[0]])]
    )
    footprints = Footprints(bbox)
    assert shape(footprints.data_polygon).area < bad_poly.area
    # Since the shape is rectangular, these should be the same
    assert shape(footprints.tile_polygon).area == shape(footprints.data_polygon).area


def test_no_footprint_shift():
    bbox = [(20, 0), (25, 0), (25, 5), (20, 5)]
    good_poly = MultiPolygon(
        [Polygon(bbox + [bbox[0]])]
    )
    footprints = Footprints(bbox)
    assert shape(footprints.data_polygon).area == good_poly.area
    # Since the shape is rectangular, these should be the same
    assert shape(footprints.tile_polygon).area == shape(footprints.data_polygon).area
