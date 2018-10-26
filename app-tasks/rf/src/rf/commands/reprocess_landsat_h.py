import logging


import click


from ..models import Scene
from ..utils.exception_reporting import wrap_rollbar
from ..uploads.landsat_historical.factories import (
    process_to_cog,
    upload_file,
    MultiSpectralScannerConfig,
    ThematicMapperConfig,
    EnhancedThematicMapperConfig
)
from rf.utils import io

logger = logging.getLogger(__name__)


@click.command(name='reprocess-landsat-h')
@click.argument('scene_id')
@wrap_rollbar
def reprocess_landsat_h(scene_id):
    logger.info('Fetching scene to reprocess with correct band order: %s', scene_id)
    scene = Scene.from_id(scene_id)
    upload_dst = scene.ingestLocation.split('/')[-1]
    landsat_id = scene.name
    gcs_prefix = io.gcs_path_for_landsat_id(landsat_id)
    path_meta = io.base_metadata_for_landsat_id(landsat_id)
    sensor = path_meta['sensor_id']
    config = {
        'M': MultiSpectralScannerConfig,
        'T': ThematicMapperConfig,
        'E': EnhancedThematicMapperConfig
    }[sensor]
    with io.get_tempdir() as prefix:
        (local_path, _) = process_to_cog(prefix, gcs_prefix, landsat_id, config)
        upload_file(scene.owner, local_path, upload_dst)
