"""Utilities for transforming public scenes into COGs"""

from rf.ingest.settings import (landsat8_band_order, sentinel2_band_order,
                                landsat8_datasource_id,
                                sentinel2_datasource_id)
from rf.utils import cog
from rf.utils.io import get_tempdir

import boto3

import logging
import os
import urllib

DATA_BUCKET = os.getenv('DATA_BUCKET')

s3client = boto3.client('s3')
logger = logging.getLogger(__name__)


def create_cog(image_locations, scene, same_path=False):
    with get_tempdir() as local_dir:
        dsts = [os.path.join(local_dir, fname) for _, fname in image_locations]
        cog.fetch_imagery(image_locations, local_dir)
        warped_paths = cog.warp_tifs(dsts, local_dir)
        merged_tif = cog.merge_tifs(warped_paths, local_dir)
        cog.add_overviews(merged_tif)
        cog_path = cog.convert_to_cog(merged_tif, local_dir)
        if same_path:
            updated_scene = upload_tif(
                cog_path, scene,
                os.path.join('user-uploads', scene.owner, '{}_COG.tif'.format(scene.id)),
                os.path.join('user-uploads', urllib.quote_plus(scene.owner), '{}_COG.tif'.format(scene.id))
            )
        else:
            updated_scene = upload_tif(cog_path, scene)
        updated_scene.update()
        os.remove(cog_path)


def upload_tif(tif_path, scene, key='', ingest_location=''):
    if len(key) == 0:
        key = os.path.join('public-cogs', '{}_COG.tif'.format(scene.id))

    s3uri = 's3://{}/{}'.format(DATA_BUCKET, key)
    ingestUri = 's3://{}/{}'.format(DATA_BUCKET, ingest_location)
    logger.info('Uploading tif to S3 at %s', s3uri)
    with open(tif_path, 'rb') as inf:
        s3client.put_object(Bucket=DATA_BUCKET, Key=key, Body=inf)
    logger.info('Tif uploaded successfully')
    scene.ingestLocation = s3uri if len(ingest_location) == 0 else ingestUri
    scene.sceneType = 'COG'
    scene.ingestStatus = 'INGESTED'
    return scene


def sort_key(datasource_id, band):
    if datasource_id == sentinel2_datasource_id:
        return sentinel2_band_order[band.name]
    elif datasource_id == landsat8_datasource_id:
        return landsat8_band_order[band.name]
    else:
        raise ValueError(
            'Trying to run public COG ingest for scene with mysterious datasource',
            datasource_id)
