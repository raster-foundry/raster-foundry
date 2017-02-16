"""Tests related to landsat 8 IO operations"""

import os
import unittest

from rf.uploads.landsat8.io import (
    get_landsat_url,
    get_tempdir
)


class Landsat8IOTestCase(unittest.TestCase):
    """Test that we can import raster foundry"""

    def test_scene_url_construction(self):
        """Verify that the scene URL to s3 can be constructed properly"""
        scene = 'LC81390452014295LGN00'
        url = get_landsat_url(scene)

        test_url = 'https://landsat-pds.s3.amazonaws.com/L8/139/045/LC81390452014295LGN00/'
        self.assertEqual(
            url, test_url, 'URL should have been {}, instead got {}'.format(test_url, url)
        )

    def test_temp_dir_cleanup(self):
        """Test that temporary directory is cleaned up even if error thrown"""
        
        try:
            with get_tempdir() as temp_dir:
                check_directory = temp_dir
                raise Exception('Dummy Exception')
        except:
            pass

        self.assertFalse(os.path.isdir(check_directory),
                         'Directory {} should have been deleted'.format(check_directory))
            