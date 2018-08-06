"""Functions related to IO of landsat 8 data"""

from contextlib import contextmanager
import os
import logging
import shutil
import tempfile

import requests


logger = logging.getLogger(__name__)


@contextmanager
def get_tempdir(debug=False):
    """Returns a temporary directory that is cleaned up after usage

    Returns:
        str
    """
    temp_dir = tempfile.mkdtemp()
    try:
        yield temp_dir
    finally:
        if not debug:
            shutil.rmtree(temp_dir)


def get_rf_image(rf_image_id):
    """Gets json representation of raster foundry image

    Used to determine what scene to process

    Args:
         rf_image_id (str): ID used for get request
    """
    rf_host = os.getenv('RF_HOST', 'https://staging.rasterfoundry.com')
    rf_token = os.getenv('RF_TOKEN')

    assert rf_token, 'No Authentication token provided in environment'

    url = '{rf_host}/api/images/{rf_image_id}/'.format(rf_host=rf_host, rf_image_id=rf_image_id)
    headers = {'Auth': 'Bearer {rf_token}'.format(rf_token=rf_token)}
    response = requests.get(url, headers=headers)
    response.raise_for_status()
    return response.json()


def upload_footprint(rf_image_id, geojson):
    """Uploads geojson for a raster foundry

    Args:
        rf_image_id (str): image id to associate footprint with
        geojson (dict): geojson that represents footprint for image
    """
    rf_host = os.getenv('RF_HOST', 'https://staging.rasterfoundry.com')
    rf_token = os.getenv('RF_TOKEN')

    assert rf_token, 'No Authentication token provided in environment'

    url = '{rf_host}/api/footprints/'.format(rf_host=rf_host)
    headers = {'Auth': 'Bearer {rf_token}'.format(rf_token=rf_token)}
    payload = {'imageId': rf_image_id, 'geojson': geojson}
    response = requests.post(url, json=payload, headers=headers)
    response.raise_for_status()


def get_records_from_csv(dict_reader, start_date, end_date, date_key, threshold=10):
    """Return records from dict_reader between start_date and end_date (left closed)

    dict_reader should come from a csv that is sorted in descending order in its
    date_key column, otherwise this won't make any sense. The LANDSAT_8.csv from
    USGS is sorted in its date_key column.

    Args:
        dict_reader (csv.DictReader): iterator to search in
        start_date (str): yyyy-mm-dd formatted datestring. "Back to" date
        end_date (str): yyy-mm-dd formatted datestring. "Up to" date
        date_key (hashable): column in csv that represents dates
        threshold (int): how many records to find past the range before giving up

    Returns:
        list[dict]
    """
    out_records = []
    wrong = 0
    while True:
        row = dict_reader.next()
        if start_date <= row['acquisitionDate'] < end_date:
            out_records.append(row)
        elif row['acquisitionDate'] < start_date:
            wrong += 1

        if wrong == threshold:
            break

    return out_records


def get_landsat_path(scene):
    """Returns download suffix path for scene

    Args:
        scene (str): landsat scene to get path

    Returns:
        str
    """

    wrs_path = scene[3:6]
    wrs_row = scene[6:9]

    path = 'L8/{wrs_path}/{wrs_row}/{scene}/'.format(
        wrs_path=wrs_path, wrs_row=wrs_row, scene=scene
    )
    logger.debug('Constructed path: %s', path)
    return path


def get_landsat_url(scene):
    """Returns download root URL for scene

    Args:
        scene (str): landsat scene to get root URL

    Returns:
        str
    """

    root_url = 'https://landsat-pds.s3.amazonaws.com/' + get_landsat_path(scene)
    logger.debug('Constructed Root URL: %s', root_url)
    return root_url


def download_tif(temp_dir, scene, bands):
    """Downloads scene bands from S3

    Args:
        temp_dir (str): temp directory to store downloads in
        scene (str): scene to download
        bands (list[int]): list of bands to download

    Returns:
        dict: maps band to filepath
    """
    assert isinstance(bands, list), 'Bands must be a list of integers'

    root_url = get_landsat_url(scene)
    downloaded_bands = {}
    for band in bands:
        url = os.path.join(root_url, '{scene}_B{band}.TIF'.format(scene=scene, band=band))
        _, download_path = tempfile.mkstemp(suffix='.TIF', dir=temp_dir)
        logger.info('Downloading %s => %s', url, download_path)
        response = requests.get(url, stream=True)
        response.raise_for_status()
        downloaded_bands[band] = download_path
        with open(download_path, 'wb') as filehandler:
            count = 1
            for chunk in response.iter_content(chunk_size=1024):
                if chunk:  # filter out keep-alive new chunks
                    filehandler.write(chunk)
                    count += 1
    return downloaded_bands
