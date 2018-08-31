from collections import OrderedDict
import glob
import logging
import os
import subprocess
import urllib

import boto3
import rasterio
import requests

from rf.models import Band, Scene
from rf.uploads.geotiff import create_geotiff_image
from rf.uploads.landsat8.io import get_tempdir
from rf.utils import io
from .parse_mtl import extract_metadata

logger = logging.getLogger(__name__)

data_bucket = os.getenv('DATA_BUCKET',
                        'rasterfoundry-development-data-us-east-1')


class MultiSpectralScannerConfig(object):
    bands = OrderedDict([('1', Band('Green', 1,
                                    [500, 600])), ('2',
                                                   Band('Red', 2, [600, 700])),
                         ('3', Band('NIR - 1', 3, [700, 800])),
                         ('4', Band('NIR - 2', 4, [800, 1100]))])


class ThematicMapperConfig(object):
    bands = OrderedDict(
        [('1', Band('Blue', 1, [450, 520])), ('2', Band(
            'Green', 2, [520, 600])), ('3', Band('Red', 3, [630, 690])),
         ('4', Band('NIR', 4, [760, 900])), ('5',
                                             Band('SWIR - 1', 5,
                                                  [1550, 1750])),
         ('6', Band('Thermal', 6, [10400, 12500])), ('7',
                                                     Band(
                                                         'SWIR - 2', 7,
                                                         [2080, 2350]))])


class EnhancedThematicMapperConfig(object):
    bands = OrderedDict(
        [('1', Band('Blue', 1, [450, 520])), ('2', Band(
            'Green', 2, [520, 600])), ('3', Band('Red', 3, [630, 690])),
         ('4', Band('NIR', 4, [760, 900])), ('5',
                                             Band('SWIR - 1', 5,
                                                  [1550, 1750])),
         ('6_VCID_1', Band('Thermal', 6,
                           [10400, 12500])), ('6_VCID_2',
                                              Band('Thermal', 7,
                                                   [10400, 12500])),
         ('7', Band('SWIR - 2', 8, [2080, 2350])), ('8',
                                                    Band(
                                                        'Panchromatic', 9,
                                                        [520, 900]))])


class LandsatHistoricalSceneFactory(object):
    def __init__(self, upload):
        self.upload = upload
        self._metadata = None

    def generate_scenes(self):
        scenes = []
        for landsat_id in self.upload.files:
            path_meta = io.base_metadata_for_landsat_id(landsat_id)
            sensor = path_meta['sensor_id']
            config = {
                'M': MultiSpectralScannerConfig,
                'T': ThematicMapperConfig,
                'E': EnhancedThematicMapperConfig
            }[sensor]
            with get_tempdir() as temp_dir:
                scene = create_scene(self.upload.owner, temp_dir, landsat_id,
                                     config, self.upload.datasource)
                scenes.append(scene)
        return scenes


def create_scene(owner, prefix, landsat_id, config, datasource):
    logger.info('Creating scene for landsat id {}'.format(landsat_id))
    gcs_prefix = io.gcs_path_for_landsat_id(landsat_id)
    logger.info('Fetching all bands')
    for band in config.bands.keys():
        fetch_band(prefix, gcs_prefix, band, landsat_id)
    metadata_resp = requests.get(io.make_path_for_mtl(gcs_prefix, landsat_id))
    if metadata_resp.status_code == 404:
        logger.error('Landsat scene %s is not available yet in GCS',
                     landsat_id)
        raise Exception('Could not find landsat scene %s', landsat_id)
    filter_metadata = extract_metadata(metadata_resp.content)
    cog_fname = '{}_COG.tif'.format(landsat_id)
    stacked_fname = '{}_STACKED.tif'.format(landsat_id)
    filenames = {
        'COG': os.path.join(prefix, cog_fname),
        'STACKED': os.path.join(prefix, stacked_fname)
    }
    convert_to_cog(prefix, filenames['STACKED'], filenames['COG'], config,
                   landsat_id)
    s3_location = upload_file(owner, filenames['COG'], cog_fname)
    logger.info('Creating image')
    ingest_location = 's3://{}/{}'.format(data_bucket,
                                          urllib.quote(s3_location))
    scene = Scene(
        'PRIVATE', [],
        datasource, {},
        landsat_id,
        'SUCCESS',
        'SUCCESS',
        'INGESTED', [io.make_path_for_mtl(gcs_prefix, landsat_id)],
        ingestLocation=ingest_location,
        cloudCover=filter_metadata['cloud_cover'],
        acquisitionDate=filter_metadata['acquisition_date'],
        sceneType='COG',
        owner=owner)
    image = create_geotiff_image(
        filenames['COG'],
        ingest_location,
        filename=cog_fname,
        owner=owner,
        scene=scene.id,
        band_create_function=lambda x: config.bands.values())
    scene.images = [image]
    return scene


def convert_to_cog(prefix, stacked_tif_path, cog_tif_path, config, landsat_id):
    own_tifs = glob.glob('/{}/{}*.TIF'.format(prefix, landsat_id))
    with rasterio.open(own_tifs[0]) as src0:
        meta = src0.meta
        meta.update(count=len(own_tifs))
    with rasterio.open(stacked_tif_path, 'w', **meta) as dst:
        for i, band in enumerate(config.bands):
            fname = os.path.join(prefix, '{}_B{}.TIF'.format(landsat_id, band))
            with rasterio.open(fname) as src:
                dst.write_band(i + 1, src.read(1))

    translate_cmd = [
        'gdal_translate', stacked_tif_path,
        os.path.join(prefix, 'translated.tif'), '-co', 'TILED=YES', '-co',
        'COMPRESS=DEFLATE', '-co', 'PREDICTOR=2'
    ]
    overviews_cmd = [
        'gdaladdo',
        '-r',
        'average',
        '--config',
        'COMPRESS_OVERVIEW', 'DEFLATE',
        os.path.join(prefix, 'translated.tif'),
    ]
    cog_cmd = [
        'gdal_translate',
        os.path.join(prefix, 'translated.tif'), cog_tif_path, '-co',
        'TILED=YES', '-co', 'COMPRESS=DEFLATE', '-co',
        'COPY_SRC_OVERVIEWS=YES'
    ]

    logger.info('Tiling input tif')
    subprocess.check_call(translate_cmd)
    logger.info('Adding overviews')
    subprocess.check_call(overviews_cmd)
    logger.info('Converting tif to COG')
    subprocess.check_call(cog_cmd)


def upload_file(owner, local_path, remote_fname):
    s3_client = boto3.client('s3')
    key = 'user-uploads/{}/{}'.format(owner, remote_fname)
    s3_client.put_object(
        Bucket=data_bucket, Key=key, Body=open(local_path, 'r'))
    logger.info("Uploading COG: %s => (Bucket: %s, Key: %s)", local_path,
                data_bucket, key)
    return key


def fetch_band(local_prefix, gcs_prefix, band, landsat_id):
    with open(
            os.path.join(local_prefix, io.make_fname_for_band(
                band, landsat_id)), 'w') as outf:
        outf.write(
            requests.get(io.make_path_for_band(gcs_prefix, band,
                                               landsat_id)).content)
