import logging
import click
import os
import uuid
import psycopg2
import psycopg2.extras
import boto3
from ..utils.exception_reporting import wrap_rollbar
from ingest_scene import ingest

logger = logging.getLogger(__name__)


@click.command(name='reprocess-sentinel')
@click.argument('scene_ids')
@wrap_rollbar
def reprocess_sentinel(scene_ids):
    """Fix a sentinel 2 scene by replacing scene metadata bands and Images

    Args:
        scene_ids (str): comma separated list of ids to reprocess
    """
    reprocess(scene_ids)


def reprocess(scene_ids):
    conn = psycopg2.connect('host={} dbname={} user={} password={}'.format(
        os.environ.get('POSTGRES_HOST', 'postgres'),
        os.environ['POSTGRES_DB'],
        os.environ['POSTGRES_USER'],
        os.environ['POSTGRES_PASSWORD'])
    )
    cur = conn.cursor(cursor_factory=psycopg2.extras.DictCursor)

    ids = [scene.strip() for scene in scene_ids.split(',')]

    logger.info('Connected to db')

    batch = boto3.client('batch', 'us-east-1')
    queue = 'queue{}Reprocess'.format(os.environ.get('ENVIRONMENT', 'Staging'))
    jobDef = get_latest_def(batch, 'job{}IngestScene'.format(os.environ.get('ENVIRONMENT', 'Staging')))

    def submit_job(sid):
        batch.submit_job(
            jobName='reprocess-scene-{}'.format(sid),
            jobQueue=queue,
            jobDefinition=jobDef['jobDefinitionArn'],
            parameters={'sceneId': sid}
        )

    for scene_id in ids:
        logger.info('Starting reprocessing scene: %s', scene_id)
        try:
            reprocess_scene_id(cur, conn, scene_id, submit_job)
        except Exception:
            conn.rollback()
            logger.exception(
                'Rolling back transaction. ' +
                'There was an error while reprocessing scene id "%s"', scene_id
            )
    conn.close()


def get_latest_def(batch, job_name):
    job_definitions = []
    response = batch.describe_job_definitions(
        status='ACTIVE',
        jobDefinitionName=job_name
    )
    job_definitions = response['jobDefinitions']
    next_token = response.get('nextToken', None)
    counter = 0
    while next_token:
        counter += 1
        response = batch.describe_job_definitions(
            status='active',
            jobDefinitionName=job_name,
            nextToken=next_token
        )
        next_token = response.get('nextToken', None)
        job_definitions += response['jobDefinitions']
    return max(job_definitions, key=lambda d: d['revision'])


def reprocess_scene_id(cur, conn, scene_id, submit_job):
    """Reprocess a scene idFilter

    Args:
        cur (psycopg2.Cursor)
        conn (psycopg2.Connection)
        scene_id (string)
    """
    scene = get_scene(cur, scene_id)
    if scene['datasource'] == '4a50cb75-815d-4fe5-8bc1-144729ce5b42' and check_if_broken(cur, scene_id):
        logger.info('Re-importing sentinel scene: %s', scene['id'])
        band11_id = str(uuid.uuid4())
        band12_id = str(uuid.uuid4())
        logger.info('Updating images')
        update_images(cur, scene, band11_id, band12_id)
        logger.info('Updating bands')
        update_bands(cur, scene, band11_id, band12_id)
        logger.info('Updating scene')
        update_scene(cur, scene)
        conn.commit()
        if scene['ingest_status'] == 'INGESTED':
            logger.info('Re-ingesting scene')
            submit_job(scene_id)
    elif scene['datasource'] == '4a50cb75-815d-4fe5-8bc1-144729ce5b42':
        logger.info('Skipping re-import for Scene: %s . Scene is not broken.', scene['id'])
    else:
        logger.info('Told to re-import non-sentinel scene: %s. Skipping', scene['id'])


def get_scene(cur, scene_id):
    """Fetch scene as using psycopg

    Args:
        cur (psycopg2.Cursor)
        scene_id (string)

    Returns: scene dict {"id", "ingest_status", "ingest_location", "scene_type", "scene_metadata", "metadata_files"}
    """
    cur.execute('''
    SELECT
    s.id, s.ingest_status, s.ingest_location, s.scene_type, s.scene_metadata, s.metadata_files, s.datasource
    FROM scenes s join images on images.scene = s.id
    WHERE s.id = %s''', (scene_id,))
    scene = cur.fetchone()
    return scene


def update_images(cur, scene, band11_id, band12_id):
    """Update existing images

    Set raw_data_bytes value of 0,
    Create new images for scene using paths / user of the other images

    Args:
        cur (psycopg2.Cursor)
        scene (dict)
        band11_id (string)
        band12_id (string
    """
    cur.execute('''
    UPDATE images SET raw_data_bytes = 0 where scene = %s;
    ''', (scene['id'],))
    cur.execute('''
        SELECT
        id, created_by, visibility, filename, sourceuri,
        image_metadata, resolution_meters, metadata_files
        FROM images WHERE scene = %s;''',
                (scene['id'],))
    existing_image = cur.fetchone()
    logger.info('Fetched existing image %s', existing_image)
    base_uri = os.path.dirname(existing_image['sourceuri'])
    band11 = {
        'id': band11_id, 'created_by': existing_image['created_by'],
        'visibility': existing_image['visibility'], 'owner': existing_image['created_by'],
        'filename': 'B11.jp2', 'sourceuri': base_uri + '/B11.jp2', 'scene': scene['id'], 'resolution_meters': '20'
    }
    band12 = {
        'id': band12_id, 'created_by': existing_image['created_by'],
        'visibility': existing_image['visibility'],
        'filename': 'B12.jp2', 'sourceuri': base_uri + '/B12.jp2', 'scene': scene['id'], 'resolution_meters': '20'
    }
    bands = (band11, band12)
    logger.info('Inserting images: %s', bands)
    cur.executemany('''
    INSERT INTO images (
    id, created_at, modified_at, created_by, modified_by,
    owner, raw_data_bytes, visibility, filename, sourceuri,
    scene, resolution_meters, metadata_files, image_metadata
    ) values (
    %(id)s, now(), now(), %(created_by)s, %(created_by)s,
    %(created_by)s, 0, %(visibility)s, %(filename)s, %(sourceuri)s,
    %(scene)s, %(resolution_meters)s, '{}', '{}'
    );
    ''', bands)


def update_bands(cur, scene, band11_id, band12_id):
    """Update the bands so ingest work correctly

    Args:
        cur (psycopg2.Cursor)
        scene (dict)
        band11_id (string)
        band12_id (string)
    """
    bands = (
        {
            'id': str(uuid.uuid4()), 'image_id': band11_id, 'name': 'short-wave infrared - 11',
            'wavelength': '{1565,1655}'
        }, {
            'id': str(uuid.uuid4()), 'image_id': band12_id, 'name': 'short-wave infrared - 12',
            'wavelength': '{2100,2280}'
        }
    )
    logger.info('Inserting bands %s', bands)
    cur.executemany('''
    INSERT INTO bands (id, image_id, name, number, wavelength)
    VALUES (%(id)s, %(image_id)s, %(name)s, 0, %(wavelength)s)
    ''', bands)


def update_scene(cur, scene):
    """Update band metadata for scene using psycopg

       For ingested scenes, change the ingest status back to uningested
       and the scene_type to COG if necessary. We will be re-ingesting avro
       scenes as COGs

    Args:
        cur (psycopg2.Cursor)
        scene (Scene): Scene to update
    """
    cur.execute('''
    UPDATE SCENES
    SET scene_type = 'COG', ingest_status = 'NOTINGESTED', ingest_location = null
    WHERE id = %s''', (scene['id'],))


def check_if_broken(cur, scene_id):
    """A scene is broken if the scene has 11 images instead of 13"""
    cur.execute('''
    SELECT count(id) from images where scene = %s
    ''', (scene_id,))
    image_count = cur.fetchone()[0]
    logger.info('Scene has %s images', image_count)
    return image_count < 13
