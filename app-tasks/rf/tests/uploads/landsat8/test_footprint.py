"""Tests related to extracting footprints"""

import json
import os
import unittest

import numpy as np
from pyproj import Proj
import rasterio

from rf.uploads.landsat8.io import get_tempdir
from rf.uploads.landsat8.footprint import (
    create_tif_mask,
    transform_polygon_coordinates,
    extract_polygon
)


class Landsat8FootprintTestCase(unittest.TestCase):
    """Test footprint extraction"""

    def setUp(self):
        cwd = os.path.abspath(os.path.dirname(__file__))
        self.landsat8_tif = os.path.join(cwd, 'data', 'landsat8-clipped.tif')
        self.mask_tif = os.path.join(cwd, 'data', 'test_mask.TIF')
        self.good_json = os.path.join(cwd, 'data', 'mask.json')

    def test_create_tif_mask(self):
        """Test that creating a tif mask works properly"""
        with get_tempdir() as temp_dir:
            new_mask_tif = create_tif_mask(temp_dir, self.landsat8_tif)
            files = os.listdir(temp_dir)
            self.assertEqual(len(files), 1, 'Should have created a mask tif')
            
            with rasterio.open(new_mask_tif) as src:
                band = src.read(1)
                self.assertEqual(band.size, 260832,
                                 'Size of band is {} instead of {}'.format(band.size, 260832))
                non_zero_pixels = np.sum(band)
                self.assertEqual(
                    non_zero_pixels, 117687,
                    'Number of pixels is {} should be {}'.format(non_zero_pixels, 117687)
                )

    def test_polygon_transform(self):
        """Test that reprojecting a polygon works properly"""
        test_feature = {"type": "Polygon", "coordinates": [
            [[45, 47.754097979680026],
             [45, 52.908902047770276],
             [55.54687499999999, 52.908902047770276],
             [55.54687499999999, 47.754097979680026],
             [45, 47.754097979680026]]
        ]}
        src_crs = Proj(init='epsg:4326')
        target_crs = Proj(init='epsg:3857')
        transformed_feature = transform_polygon_coordinates(test_feature, src_crs, target_crs)
        transformed_coordinates = [[(5009377.085697311, 6066042.564711587),
                                    (5009377.085697311, 6966165.0097978255),
                                    (6183449.840157617, 6966165.0097978255),
                                    (6183449.840157617, 6066042.564711587),
                                    (5009377.085697311, 6066042.564711587)]]
        self.assertEqual(transformed_feature['coordinates'], transformed_coordinates,
                         'Coordinates did not match after transformation')

    def test_extract_polygon(self):
        """Test that extracting a polygon works correctly"""
        feature = extract_polygon(self.mask_tif)
        num_features = len(feature['features'])

        coords = feature['features'][0]['geometry']['coordinates'][0]
        num_coords = len(coords)
        
        with open(self.good_json, 'rb') as fh:
            good_json = json.loads(fh.read())
        self.assertEqual(num_features, 1,
                         'Should have only extracted one feature, got {}'.format(num_features))
        
        good_json_coords = good_json['features'][0]['geometry']['coordinates'][0]
        self.assertEqual(
            good_json_coords[0], list(coords[0]), 'Coordinates did not match (produced {}, should be {}'.format(
                coords[0], good_json_coords[0]
            )
        )

        num_good_coords = len(good_json_coords)        
        self.assertEqual(num_good_coords, num_coords, 'Number of coordinates did not match')