from copy import deepcopy
import json
import logging
import os
import subprocess
from urlparse import urlparse

import boto3
import click

from ..utils.exception_reporting import wrap_rollbar
from ..utils.io import s3_upload_export, dropbox_upload_export, get_tempdir
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
        with get_tempdir(True) as local_dir:
            logger.info('Created Working Directory %s', local_dir)
            logger.info('Rewriting Export Definition')
            local_path = write_export_definition(export_definition, local_dir)
            logger.info('Rewrote export definition to %s', local_path)
            logger.info('Preparing to Run Export')
            run_export('file://' + local_path, export_id)
            out_path = local_tif_path_from_export_definition(
                export_definition, local_dir
            ).replace('file://', '')
            upload_processed_tif(out_path, export_definition)
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
            logger.info('Sending email notifications for export %s on try: %s',
                        export_id, RETRY)
            update_export_status(export_id, final_status)
        else:
            logger.info('Export failed, on try %s/3', RETRY)


def local_tif_path_from_export_definition(export_definition, local_dir):
    return 'file://{}/{}.tif'.format(local_dir, export_definition['id'])


def create_export_definition(export_id):
    """Create an export definition

    Args:
        export_id (str): ID of export job to process
    """
    bucket = os.getenv('DATA_BUCKET')
    key = 'export-definitions/{}'.format(export_id)
    logger.info('Creating export definition for %s', export_id)
    command = [
        'java', '-cp', '/opt/raster-foundry/jars/batch-assembly.jar',
        'com.rasterfoundry.batch.Main', 'create_export_def', export_id, bucket,
        key
    ]
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
    copied['output']['destination'] = local_tif_path_from_export_definition(
        export_definition, local_dir)
    with open(local_ed_path, 'w') as outf:
        outf.write(json.dumps(copied))
    return local_ed_path


def upload_processed_tif(merged_tiff_path, export_definition):
    """Uses original export definition location to upload to s3 or dropbox"""
    dest = os.path.join(export_definition['output']['destination'], 'export.tif')
    logger.info('Preparing to upload processed export to: %s', dest)
    export_type = 's3' if dest.startswith('s3') else 'dropbox'
    dropbox_access_token = export_definition['output'].get('dropboxCredential')
    if export_type == 's3':
        s3_upload_export(merged_tiff_path, dest)
    else:
        dropbox_upload_export(dropbox_access_token, merged_tiff_path,
                              dest.replace('dropbox:///', '/'))


def run_export(export_s3_uri, export_id):
    """Run spark export

    Args:
        export_s3_uri (str): location of job definition
        export_id (str): ID of export job to process

    """
    status_uri = 's3://{}/export-statuses/{}'.format(
        os.getenv('DATA_BUCKET'), export_id)

    command = [
        'java', '-jar',
        '/opt/raster-foundry/jars/backsplash-export-assembly.jar', '-d',
        export_s3_uri
    ]
    subprocess.check_call(command)
    logger.info('Finished exporting %s', export_s3_uri)
    return status_uri


def update_export_status(export_id, export_status):
    """Update an export's status based on export result

    Args:
        export_id (str): ID of the export to update
        export_status (str): The status this export ended with
    """

    command = [
        'java', '-cp', '/opt/raster-foundry/jars/batch-assembly.jar',
        'com.rasterfoundry.batch.Main', 'update_export_status', export_id,
        export_status
    ]
    logger.info('Preparing to update export status with command: %s', command)
    output = subprocess.check_output(command)
    logger.info('Output from update command was: %s', output)
