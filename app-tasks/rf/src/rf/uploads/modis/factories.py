from datetime import datetime
from functools import partial
import logging
import os
import uuid
import urllib

from rf.models import Band
from rf.models import Scene
from rf.uploads.geotiff import create_geotiff_image
from rf.uploads.landsat8.io import get_tempdir
from rf.uploads.modis.create_geotiff import create_geotiffs
from rf.uploads.modis.download_modis import download_hdf
from rf.utils.io import (
    upload_tifs,
    IngestStatus,
    JobStatus,
    Visibility
)

logger = logging.getLogger(__name__)

modis_configs = {
    'a11b768b-d869-476e-a1ed-0ac3205ed761': {
        'thumbnail_bands': [1, 4, 3],
        'bands': {
            'B01.tif': Band('Red', 0, [620, 670]),
            'B02.tif': Band('NIR', 0, [841, 876]),
            'B03.tif': Band('Blue', 0, [459, 479]),
            'B04.tif': Band('Green', 0, [545, 565]),
            'B05.tif': Band('SWIR - 1', 0, [1230, 1250]),
            'B06.tif': Band('SWIR - 2', 0, [1628, 1652]),
            'B07.tif': Band('SWIR - 3', 0, [2105, 2155]),
            'B08.tif': Band('Reflectance Band Quality', 0, [0, 0]),
            'B09.tif': Band('Solar Zenith Angle', 0, [0, 0]),
            'B10.tif': Band('View Zenith Angle', 0, [0, 0]),
            'B11.tif': Band('Relative Azimuth Angle', 0, [0, 0]),
            'B12.tif': Band('State Flags', 0, [0, 0]),
            'B13.tif': Band('Day of Year', 0, [0, 0])
        }
    },
    '55735945-9da5-47c3-8ae4-572b5e11205b': {
        'thumbnail_bands': [1, 4, 3],
        'bands': {
            'B01.tif': Band('Red', 0, [620, 670]),
            'B02.tif': Band('NIR', 0, [841, 876]),
            'B03.tif': Band('Blue', 0, [459, 479]),
            'B04.tif': Band('Green', 0, [545, 565]),
            'B05.tif': Band('SWIR - 1', 0, [1230, 1250]),
            'B06.tif': Band('SWIR - 2', 0, [1628, 1652]),
            'B07.tif': Band('SWIR - 3', 0, [2105, 2155]),
            'B08.tif': Band('Reflectance Band Quality', 0, [0, 0]),
            'B09.tif': Band('Solar Zenith Angle', 0, [0, 0]),
            'B10.tif': Band('View Zenith Angle', 0, [0, 0]),
            'B11.tif': Band('Relative Azimuth Angle', 0, [0, 0]),
            'B12.tif': Band('State Flags', 0, [0, 0]),
            'B13.tif': Band('Day of Year', 0, [0, 0])
        }
    }
}


class MODISSceneFactory(object):
    def __init__(self, hdf_urls, datasource, upload, project_id=None, visibility=Visibility.PRIVATE, owner=None):
        """Create factory for generating MODIS scenes

        Args:
            hdf_urls (list[str]): list of URLs for MODIS scenes to create
            datasource (str): ID of MODIS datasource
            upload (str): ID of upload scene creation is associated with
            project_id (str): optional project to associate with uploads
            visibility (str): level of visibility for new scene
            owner (str): optional owner to set for scene
        """
        self.hdf_urls = hdf_urls
        self.datasource = datasource
        self.upload = upload
        self.project_id = project_id
        self.visibility = visibility
        self.owner = owner

    def generate_scenes(self):
        scenes = []
        for hdf_url in self.hdf_urls:
            with get_tempdir() as temp_dir:
                scene = create_scene(hdf_url, temp_dir, self.owner, self.datasource)
                scenes.append(scene)
        return scenes


def create_scene(hdf_url, temp_directory, user_id, datasource):
    """Create a MODIS scene

    Args:
        hdf_url (str): URL for MODIS scene to download
        temp_directory (str): directory to use as scratch space when creating scene
        user_id (str): ID of owner for new MODIS scene
        datasource (str): ID of datasource for new MODIS scene
    """
    config = modis_configs[datasource]
    granule_parts = os.path.basename(hdf_url).split('.')

    acquisition_datetime = datetime.strptime(granule_parts[1][1:], '%Y%j')
    name = '.'.join(granule_parts[:-1])
    id = str(uuid.uuid4())

    scene = Scene(Visibility.PRIVATE, [], datasource, {}, name,
                  JobStatus.SUCCESS, JobStatus.SUCCESS, IngestStatus.INGESTED, [], owner=user_id, id=id,
                  acquisitionDate=acquisition_datetime.isoformat() + 'Z', cloudCover=0)

    hdf_filepath = download_hdf(hdf_url, temp_directory)

    tifs = create_geotiffs(hdf_filepath, temp_directory)

    s3_uris = upload_tifs(tifs, user_id, scene.id)

    images = []
    get_band_func = partial(get_image_band, modis_config=config)
    for local_path, remote_path in zip(tifs, s3_uris):

        image = create_geotiff_image(local_path, urllib.unquote(s3_uris[0]),
                                     scene=scene.id, owner=user_id, band_create_function=get_band_func)
        images.append(image)
    scene.images = images
    scene.ingestLocation = s3_uris[0]
    scene.sceneType = 'COG'

    return scene


def get_image_band(filepath, modis_config=None):
    """Helper function to get bands for a particular modis scene

    Args:
        modis_config (dict): dictionary of configuration for a particular MODIS datasource
        filepath (str): path to file to get image band for
    """
    bands = modis_config['bands'].values()
    if not bands:
        logger.warn('Could not find bands for file: %s', filepath)
    return bands
