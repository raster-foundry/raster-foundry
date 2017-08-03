# Factory to create Raster Foundry scenes from Planet Labs scenes
import logging
import os
import tempfile
import time

import boto3
from botocore.errorfactory import ClientError
from planet import api

from rf.utils.io import Visibility, download_s3_obj_by_key
from .create_scenes import create_planet_scene


logger = logging.getLogger(__name__)


class PlanetSceneFactory(object):
    """A convenience class for creating scenes from several Planet IDs

    Example usage:
    ```
        factory = PlanetSceneFactory(['<List of Planet IDs Here>'])
        for scene in factory.generate_scenes():
            # do something with the created scenes
    ```
    """

    def __init__(self, planet_ids, datasource, organization_id, upload_id,
                 visibility=Visibility.PRIVATE, tags=[], owner=None, client=None):
        self.organizationId = organization_id
        self.upload_id = upload_id
        self.datasource = datasource
        self.visibility = visibility
        self.tags = tags
        self.owner = owner
        self.client = client
        self.planet_ids = planet_ids

    def generate_scenes(self):
        """Create a generator to """
        for planet_id in self.planet_ids:
            planet_feature = self.copy_asset_to_s3(planet_id)
            yield create_planet_scene(
                planet_feature, self.datasource, self.organizationId, self.visibility,
                self.tags, self.owner
            )

    def copy_asset_to_s3(self, planet_id):
        """Make the Planet tif available to Rater Foundry

        This function downloads the basic analytic tif from planet,
        then reuploads it to s3 in a directory for planet files.

        Args:
            planet_id (str): id of the planet image to download

        Returns:
            dict: geojson for the overview of the planet tif
        """
        s3_client = boto3.client('s3')
        item_type, item_id = planet_id.split(':')
        item = self.client.get_item(item_type, item_id).get()
        if item_type.startswith('RE'):
            raise Exception('RapidEye items don\'t have assets. Bailing.')
        item_id = item['id']
        asset_type = 'basic_analytic'
        s3_path = 'user-uploads/{}/{}/{}-{}-{}.tif'.format(
            self.owner, self.upload_id, item_type, item_id, asset_type
        )
        bucket = os.getenv('DATA_BUCKET')

        # Downloading imagery is costly, so don't bother if someone has already
        # added this image and asset type
        try:
            obj = s3_client.get_object(Bucket=bucket, Key=s3_path)
            tf = download_s3_obj_by_key(bucket, s3_path)
            item['added_props'] = {}
            item['added_props']['localPath'] = tf
            item['added_props']['s3Location'] = 's3://{}/{}'.format(bucket, s3_path)
            return item
        except ClientError:
            logger.info('%s has never been imported. Downloading and copying',
                        item_id)

        # Get and activate assets for the desired item
        assets = self.client.get_assets_by_id(item_type, item_id).get()
        self.client.activate(assets[asset_type])
        try_number = 0
        logger.info('Activating asset for %s', item_id)
        while assets[asset_type]['status'] != 'active':
            if try_number % 5 == 0 and try_number > 0:
                logger.info('Status after %s tries: %s',
                            try_number,
                            assets[asset_type]['status'])
            assets = self.client.get_assets_by_id(item_type, item_id).get()
            time.sleep(15)

        # Download the assets for the desired item and write to a tempfile
        logger.info('Downloading asset for %s', item_id)
        body = self.client.download(assets[asset_type]).get_body()
        tf = tempfile.mktemp()
        with open(tf, 'wb') as outf:
            body.write(file=outf)

        # Upload assets for the desired item to s3
        logger.info('Copying asset for %s to s3', item_id)
        with open(tf, 'rb') as inf:
            s3_client.put_object(
                Bucket=bucket,
                Body=inf,
                Key=s3_path
            )
        item['added_props'] = {}
        item['added_props']['localPath'] = tf
        item['added_props']['s3Location'] = 's3://{}/{}'.format(bucket, s3_path)
        # Return the json representation of the item
        return item
