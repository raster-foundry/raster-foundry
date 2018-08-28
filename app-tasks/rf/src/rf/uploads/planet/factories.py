# Factory to create Raster Foundry scenes from Planet Labs scenes
import logging
import os
import time
from xml.dom import minidom

import boto3
import requests
from retrying import retry

from rf.uploads.geotiff.utils import convert_to_cog
from rf.uploads.landsat8.io import get_tempdir
from rf.utils.io import Visibility
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

    def __init__(self, planet_ids, datasource, upload_id, client,
                 project_id=None, visibility=Visibility.PRIVATE, tags=[],
                 owner=None):
        self.upload_id = upload_id
        self.isProjectUpload = project_id is not None
        self.datasource = datasource
        self.visibility = visibility
        self.tags = tags
        self.owner = owner
        self.client = client
        self.planet_ids = planet_ids

    def generate_scenes(self):
        # If this upload is not associated with a project, set the scene's
        # ingest status to TOBEINGESTED so that scene creation will kick off
        # an ingest. Otherwise, set the status to NOTINGESTED, so that the status
        # will be updated when the scene is added to this upload's project
        for planet_id in set(self.planet_ids):
            logger.info('Preparing to copy planet asset to s3: %s', planet_id)
            with get_tempdir() as prefix:
                planet_feature, temp_tif_file = self.copy_asset_to_s3(prefix, planet_id)
                planet_key = self.client.auth.value
                planet_scene = create_planet_scene(
                    planet_feature, self.datasource, planet_key,
                    self.visibility, self.tags, self.owner
                )
                yield planet_scene

    def copy_asset_to_s3(self, prefix, planet_id):
        """Make the Planet tif available to Rater Foundry

        This function downloads the basic analytic tif from planet,
        then reuploads it to s3 in a directory for planet files.

        Args:
            planet_id (str): id of the planet image to download

        Returns:
            dict: geojson for the overview of the planet tif
        """

        item_type, item_id = planet_id.split(':')
        logger.info('Retrieving item type %s with id %s', item_type, item_id)
        item = self.get_item(item_id, item_type)
        item_id = item['id']

        assets = self.get_assets_by_id(item_id, item_type)
        asset_type = PlanetSceneFactory.get_asset_type(assets)
        updated_assets = self.activate_asset_and_wait(asset_type, assets, item_id, item_type)

        temp_tif_file = self.download_planet_tif(prefix, asset_type, updated_assets, item_id)
        cog_path = convert_to_cog(prefix, temp_tif_file)
        bucket, s3_path = self.upload_planet_tif(asset_type, item_id, item_type, cog_path)

        analytic_xml = self.get_analytic_xml(assets, item_id, item_type)
        reflectance_coefficients = PlanetSceneFactory.get_reflectance_coefficients(analytic_xml)
        item['properties'].update(reflectance_coefficients)

        item['added_props'] = {}
        item['added_props']['localPath'] = cog_path
        item['added_props']['s3Location'] = 's3://{}/{}'.format(bucket, s3_path)

        # Return the json representation of the item
        return item, temp_tif_file

    @retry(wait_fixed=5000, stop_max_attempt_number=5)
    def download_asset(self, asset_type, assets):
        """

        Args:
            asset_type (str): type of asset (analytic/basic_analytic)
            assets (dict): assets dictionary

        Returns:
            bytes
        """
        body = self.client.download(assets[asset_type]).get_body()
        return body

    @retry(wait_fixed=5000, stop_max_attempt_number=5)
    def get_assets_by_id(self, item_id, item_type):
        """Helper method to enable retry on Planet API errors

        Args:
            item_id (str): id of item requesting from planet
            item_type (str): type of item requesting

        Returns:
            dict
        """
        logger.info('Requesting asset from Planet: %s, %s', item_id, item_type)
        assets = self.client.get_assets_by_id(item_type, item_id).get()
        return assets

    @retry(wait_fixed=5000, stop_max_attempt_number=5)
    def get_item(self, item_id, item_type):
        """Helper method to enable we retry on failures in case Planet API errors

        Args:
            item_id (str): id of item requesting from planet
            item_type (str): type of item requesting

        Returns:
            item
        """
        item = self.client.get_item(item_type, item_id)
        logger.info('Retrieved Item: %s', item)
        return item.get()

    @retry(wait_fixed=5000, stop_max_attempt_number=5)
    def activate(self, asset_type, assets):
        """Wrapper for Planet API to be fault tolerant

        Args:
            asset_type (str): type of asset (analytic/basic_analytic)
            assets (dict): assets with related info in a dictionary

        Returns:
            None
        """
        self.client.activate(assets[asset_type])

    def get_analytic_xml(self, asset_dict, item_id, item_type):
        """Helper function to get analytic XML

        Args:
            item_type (str): type of asset to requesting
            item_id (str): id of asset
            asset_dict (dict): dictionary of assets related to a planet scene ID

        Returns:
            str
        """
        assets = self.activate_asset_and_wait('analytic_xml', asset_dict, item_id, item_type)
        xml_loc = assets['analytic_xml']['location']
        response = requests.get(xml_loc)
        return minidom.parseString(response.text)

    @staticmethod
    def get_reflectance_coefficients(xml_doc):
        """Parse reflectance coefficients from XML

        Args:
            xml_doc (XMLDoc): parsed xml document from planet

        Returns:
            dict
        """
        coefficients = {}
        nodes = xml_doc.getElementsByTagName("ps:bandSpecificMetadata")
        for node in nodes:
            bn = node.getElementsByTagName("ps:bandNumber")[0].firstChild.data
            if bn not in ['1', '2', '3', '4', '5']:
                continue
            i = int(bn)
            value = node.getElementsByTagName("ps:reflectanceCoefficient")[0].firstChild.data
            key = 'band_{}_reflectance_coeff'.format(i)
            coefficients[key] = float(value)
        return coefficients

    @staticmethod
    def get_asset_type(asset_dict):
        """Helper function to get first asset (analytic/basic_analytic)

        This varies by satellite so this covers our bases

        Args:
            asset_dict (dict): dictionary of assets related to a planet scene ID

        Returns:
            str
        """
        acceptable_types = ['analytic', 'basic_analytic', 'analytic_dn', 'basic_analytic_dn']
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
            dict
        """
        self.activate(asset_type, assets)
        try_number = 0
        logger.info('Activating asset for %s', item_id)
        while assets[asset_type]['status'] != 'active':
            if try_number % 5 == 0 and try_number > 0:
                logger.info('Status after %s tries: %s', try_number, assets[asset_type]['status'])
            assets = self.get_assets_by_id(item_id, item_type)
            time.sleep(15)

        logger.info('Asset activated: %s', item_id)
        return assets

    def download_planet_tif(self, prefix, asset_type, assets, item_id):
        """Downloads asset to local filesystem, returns path

        Args:
            asset_type (str): type of asset to download
            assets (dict): dictionary of assets
            item_id (str): planet id of scene

        Returns:
            str
        """

        base_fname = '{}.tif'.format(item_id)
        out_path = os.path.join(prefix, base_fname)
        logger.info('Downloading asset: %s to %s', item_id, out_path)

        try:
            body = self.download_asset(asset_type, assets)
        except Exception:
            logger.exception('Failed to download asset %s with %s', asset_type, assets)
            raise

        with open(out_path, 'wb') as outf:
            body.write(file=outf)

        return base_fname

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
