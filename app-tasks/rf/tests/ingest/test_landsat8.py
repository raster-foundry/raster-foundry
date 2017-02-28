"""Tests related to creating ingest definition"""

import json
import os
import unittest

from rf.models import Scene
from rf.ingest.landsat8_ingest import get_landsat8_layer

class Landsat8LayerTestCase(unittest.TestCase):
    """Test that we can create a layer from Landsat 8 scenes"""

    def setUp(self):
        cwd = os.path.abspath(os.path.dirname(__file__))
        scene_path = os.path.join(cwd, 'data', 'scene.json')
        with open(scene_path) as fh:
            self.scene = Scene.from_dict(json.load(fh))

    def test_create_layer(self):
        """Minimal test to verify that a layer can be created"""
        layer = get_landsat8_layer(self.scene)
        num_sources = len(layer.sources)
        self.assertEqual(
            num_sources, 11, 'Found {} sources, expected 11'.format(num_sources)
        )


