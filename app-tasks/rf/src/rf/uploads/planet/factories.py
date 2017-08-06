# Factory to create Raster Foundry scenes from Planet Labs scenes
import logging
import os
import tempfile
import time

import boto3

from rf.utils.io import Visibility, delete_file
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
            planet_feature, temp_tif_file = self.copy_asset_to_s3(planet_id)
            planet_key = self.client.auth.value
            planet_scene = create_planet_scene(planet_feature, self.datasource, self.organizationId, planet_key,
                                              self.visibility, self.tags, self.owner)
            delete_file(temp_tif_file)
            yield planet_scene

    def copy_asset_to_s3(self, planet_id):
        """Make the Planet tif available to Rater Foundry

        This function downloads the basic analytic tif from planet,
        then reuploads it to s3 in a directory for planet files.

        Args:
            planet_id (str): id of the planet image to download

        Returns:
            dict: geojson for the overview of the planet tif
        """

        item_type, item_id = planet_id.split(':')
        item = self.client.get_item(item_type, item_id).get()
        item_id = item['id']

        assets = self.client.get_assets_by_id(item_type, item_id).get()
        asset_type = PlanetSceneFactory.get_asset_type(assets)
        self.activate_asset_and_wait(asset_type, assets, item_id, item_type)

        temp_tif_file = self.download_planet_tif(asset_type, assets, item_id)
        bucket, s3_path = self.upload_planet_tif(asset_type, item_id, item_type, temp_tif_file)

        item['added_props'] = {}
        item['added_props']['localPath'] = temp_tif_file
        item['added_props']['s3Location'] = 's3://{}/{}'.format(bucket, s3_path)

        # Return the json representation of the item
        return item, temp_tif_file

    @staticmethod
    def get_asset_type(asset_dict):
        """Helper function to get first asset (analytic/basic_analytic)

        This varies by satellite so this covers our bases

        Args:
            asset_dict (dict): dictionary of assets related to a planet scene ID

        Returns:
            str
        """
        acceptable_types = ['analytic', 'basic_analytic']
        logger.info('Determining Analytics Asset Type')
        for acceptable_type in acceptable_types:
            if acceptable_type in asset_dict:
                logger.info('Found acceptable type: %s', acceptable_type)
                return acceptable_type
        raise Exception('No acceptable asset types found: %s', asset_dict.keys())

    def activate_asset_and_wait(self, asset_type, assets, item_id, item_type):
        """Activate and asset to prepare for it to download

        Args:
            asset_type (str): type of asset (analytic/basic_analytic)
            assets (dict): assets with related info in a dictionary
            item_id (str): planet id of scene
            item_type (str): satellite type of scene

        Returns:
            None
        """
        self.client.activate(assets[asset_type])
        try_number = 0
        logger.info('Activating asset for %s', item_id)
        while assets[asset_type]['status'] != 'active':
            if try_number % 5 == 0 and try_number > 0:
                logger.info('Status after %s tries: %s', try_number, assets[asset_type]['status'])
            assets = self.client.get_assets_by_id(item_type, item_id).get()
            time.sleep(15)

        logger.info('Asset activated: %s', item_id)

    def download_planet_tif(self, asset_type, assets, item_id):
        """Downloads asset to local filesystem, returns path

        Args:
            asset_type (str): type of asset to download
            assets (dict): dictionary of assets
            item_id (str): planet id of scene

        Returns:
            str
        """

        _, temp_tif_file = tempfile.mkstemp()
        logger.info('Downloading asset: %s to %s', item_id, temp_tif_file)

        body = self.client.download(assets[asset_type]).get_body()
        with open(temp_tif_file, 'wb') as outf:
            body.write(file=outf)

        return temp_tif_file

    def upload_planet_tif(self, asset_type, item_id, item_type, temp_tif_file):
        """Uploads planet tif to S3 -- returns bucket and path of tile

        Args:
            asset_type (str): type of asset to upload
            item_id (str): planet id to upload
            item_type (str): sensor/satellite type of item to upload
            temp_tif_file (str): path to temp file to upload

        Returns:
            (str, str)
        """
        s3_client = boto3.client('s3')
        s3_path = 'user-uploads/{}/{}/{}-{}-{}.tif'.format(self.owner, self.upload_id, item_type, item_id, asset_type)
        bucket = os.getenv('DATA_BUCKET')
        logger.info('Copying asset: %s (%s => s3://%s/%s)', item_id, temp_tif_file, bucket, s3_path)
        with open(temp_tif_file, 'rb') as inf:
            s3_client.put_object(
                Bucket=bucket,
                Body=inf,
                Key=s3_path
            )
        return bucket, s3_path
