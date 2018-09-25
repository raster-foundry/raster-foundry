from copy import deepcopy
import json
import logging
import os
import subprocess
from urlparse import urlparse

import boto3
import click
from rasterfoundry.api import API

from rf.uploads.landsat8.io import get_tempdir
from ..utils.exception_reporting import wrap_rollbar
from ..utils.io import s3_upload_export, dropbox_upload_export, get_jwt
logger = logging.getLogger(__name__)

HOST = os.getenv('RF_HOST')
API_PATH = '/api/exports/'
RETRY = os.getenv('AWS_BATCH_JOB_ATTEMPT', '-1')


@click.command(name='export')
@click.argument('export_id')
@wrap_rollbar
def export(export_id):
    """Perform export configured by user

    Args:
        export_id (str): ID of export job to process
    """
    logger.info('Creating Export Definition')
    final_status = 'EXPORTED'
    try:
        export_uri = create_export_definition(export_id)
        logger.info('Retrieving Export Definition %s', export_uri)
        export_definition = get_export_definition(export_uri)
        with get_tempdir() as local_dir:
            logger.info('Created Working Directory %s', local_dir)
            logger.info('Rewriting Export Definition')
            local_path = write_export_definition(export_definition, local_dir)
            logger.info('Rewrote export definition to %s', local_path)
            logger.info('Preparing to Run Export')
            run_export('file://' + local_path, export_id)
            logger.info('Post Processing Tiffs')
            merged_tiff_path = post_process_exports(export_definition, local_dir)
            logger.info('Uploading Processed Tiffs')
            upload_processed_tif(merged_tiff_path, export_definition)
    except subprocess.CalledProcessError as e:
        logger.error('Output from failed command: %s', e.output)
        final_status = 'FAILED'
        raise e
    except Exception as e:
        logger.error('Wrapper error: %s', e)
        final_status = 'FAILED'
        raise e
    finally:
        # The max number of retries is currently hardcoded in batch.tf
        # in the deployment repo. Please make sure that both areas are updated if
        # this needs to be changed to a configurable variable
        if final_status == 'EXPORTED' or int(RETRY) >= 3:
            logger.info('Sending email notifications for export %s on try: %s', export_id, RETRY)
            update_export_status(export_id, final_status)
        else:
            logger.info('Export failed, on try %s/3', RETRY)


def create_export_definition(export_id):
    """Create an export definition

    Args:
        export_id (str): ID of export job to process
    """
    bucket = os.getenv('DATA_BUCKET')
    key = 'export-definitions/{}'.format(export_id)
    logger.info('Creating export definition for %s', export_id)
    command = ['java', '-cp',
               '/opt/raster-foundry/jars/rf-batch.jar',
               'com.azavea.rf.batch.Main', 'create_export_def',
               export_id, bucket, key]
    logger.info('Running Command %s', ' '.join(command))
    subprocess.check_call(command)

    logger.info('Created export definition for %s', export_id)
    return 's3://{}/{}'.format(bucket, key)


def get_export_definition(export_uri):
    """Reads export definition from S3, returns dict"""
    s3 = boto3.resource('s3')

    parsed_uri = urlparse(export_uri)
    logger.info('Downloading export defintion %s', export_uri)
    data = s3.Object(parsed_uri.netloc, parsed_uri.path[1:]).get()['Body']
    return json.load(data)


def write_export_definition(export_definition, local_dir):
    """Returns local path where tiffs will be exported"""
    local_ed_path = os.path.join(local_dir, 'export_definition.json')
    copied = deepcopy(export_definition)
    # Only two slashes in file:// because local_dir is an absolute unix path
    # copied['output']['source'] = 's3://rasterfoundry-development-data-us-east-1/chris-test-local/'
    copied['output']['source'] = 'file://{}'.format(local_dir)
    # copied['output']['source'] = 'file:/{}.tif'.format(local_dir)
    with open(local_ed_path, 'w') as outf:
        outf.write(json.dumps(copied))
    return local_ed_path


def post_process_exports(export_definition, tiff_directory):
    """Run GDAL commands to make exports sane

    Returns: path to final tif file
    """
    jwt = get_jwt()
    root_url = os.getenv('RF_HOST')
    parsed_url = urlparse(root_url)
    api = API(api_token=jwt, host=parsed_url.netloc, scheme=parsed_url.scheme)
    logger.info('Retrieving Export: %s', export_definition['id'])
    export_obj = api.client.Imagery.get_exports_uuid(uuid=export_definition['id']).result()
    temp_geojson = os.path.join(tiff_directory, 'cut.json')
    with open(temp_geojson, 'wb') as fh:
        geojson = {
            'type': 'FeatureCollection',
            'features': [
                {
                    "type": "Feature",
                    "properties": {},
                    "geometry": export_obj.exportOptions.mask
                }
            ]
        }
        json.dump(geojson, fh)

    tiff_files = [os.path.join(tiff_directory, f) for f in os.listdir(tiff_directory) if f.endswith('tiff')]
    logger.info('Found %s files to merge', len(tiff_files))
    merged_tiff = os.path.join(tiff_directory, 'combined.tiff')
    translated_tiff = os.path.join(tiff_directory, 'translated.tiff')
    export_tiff = os.path.join(tiff_directory, 'export.tiff')
    merge_command = ['gdal_merge.py', '-o', merged_tiff] + tiff_files
    warp_command = ['gdalwarp', '-co', 'COMPRESS=LZW',
                    '-co', 'TILED=YES', '-crop_to_cutline',
                    '-cutline', temp_geojson, merged_tiff, export_tiff]
    translate_command = ['gdal_translate', '-co', 'COMPRESS=LZW', '-co', 'TILED=YES', '-stats',
                         export_tiff, translated_tiff]
    logger.info('Running Merge Command: %s', ' '.join(merge_command))
    subprocess.check_call(merge_command)
    logger.info('Running Warp Command: %s', ' '.join(warp_command))
    subprocess.check_call(warp_command)
    logger.info('Running Translate Command: %s', ' '.join(translate_command))
    subprocess.check_call(translate_command)
    logger.info('Finished post-processing export: %s', export_tiff)
    return translated_tiff


def upload_processed_tif(merged_tiff_path, export_definition):
    """Uses original export definition location to upload to s3 or dropbox"""
    dest = os.path.join(export_definition['output']['source'], 'export.tif')
    logger.info('Preparing to upload processed export to: %s', dest)
    export_type = 's3' if dest.startswith('s3') else 'dropbox'
    dropbox_access_token = export_definition['output'].get('dropboxCredential')
    if export_type == 's3':
        s3_upload_export(merged_tiff_path, dest)
    else:
        dropbox_upload_export(dropbox_access_token, merged_tiff_path, dest.replace('dropbox:///', '/'))


def run_export(export_s3_uri, export_id):
    """Run spark export

    Args:
        export_s3_uri (str): location of job definition
        export_id (str): ID of export job to process

    """
    export_cores = os.getenv('LOCAL_INGEST_CORES', 32)
    export_memory = os.getenv('LOCAL_INGEST_MEM_GB', 48)
    status_uri = 's3://{}/export-statuses/{}'.format(os.getenv('DATA_BUCKET'), export_id)

    command = ['spark-submit',
               '--master', 'local[{}]'.format(export_cores),
               '--driver-memory', '{}g'.format(export_memory),
               '--class', 'com.azavea.rf.batch.export.spark.Export',
               '/opt/raster-foundry/jars/rf-batch.jar',
               '-j', export_s3_uri, '-b', status_uri]
    output = subprocess.check_output(command)
    logger.info('Output from export command was:\n%s', output)
    logger.info('Finished exporting %s in spark local', export_s3_uri)
    return status_uri


def update_export_status(export_id, export_status):
    """Update an export's status based on export result

    Args:
        export_id (str): ID of the export to update
        export_status (str): The status this export ended with
    """

    command = [
        'java', '-cp',
        '/opt/raster-foundry/jars/rf-batch.jar',
        'com.azavea.rf.batch.Main',
        'update_export_status',
        export_id,
        export_status
    ]
    logger.info('Preparing to update export status with command: %s', command)
    output = subprocess.check_output(command)
    logger.info('Output from update command was: %s', output)
