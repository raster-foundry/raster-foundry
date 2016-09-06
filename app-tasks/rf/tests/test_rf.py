"""Test that we can import the library"""

import rf

import unittest


class RasterFoundryTestCase(unittest.TestCase):
    """Test that we can import raster foundry"""

    def test_rf(self):
        assert rf  # use your library here
