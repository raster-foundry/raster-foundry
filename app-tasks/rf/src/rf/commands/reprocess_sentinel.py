import logging
import click
import os
import uuid
import psycopg2
import psycopg2.extras
from ..utils.exception_reporting import wrap_rollbar
from ingest_scene import ingest_scene

logger = logging.getLogger(__name__)


@click.command(name='reprocess-sentinel')
@click.argument('scene_id')
@wrap_rollbar
def reprocess_sentinel(scene_id):
    """Fix a sentinel 2 scene by replacing scene metadata bands and Images

    Args:
        scene_id (str): Scene id to reprocess
    """
    print("Starting reprocessing scene: %s" % (scene_id, ))
    conn = psycopg2.connect("host=%s dbname=%s user=%s password=%s" % (
        os.environ.get('POSTGRES_HOST', 'postgres'),
        os.environ['POSTGRES_DB'], os.environ['POSTGRES_USER'], os.environ['POSTGRES_PASSWORD']))
    cur = conn.cursor(cursor_factory=psycopg2.extras.DictCursor)
    # nondict_cur = conn.cursor()
    print("Connected to db")
    scene = get_scene(cur, scene_id)
    if check_if_broken(cur, scene_id):
        print("Re-importing sentinel scene: %s" % (scene['id']))
        # delete_images(cur, scene_id) # not necessary, just add images for 11 and 12
        band11_id = str(uuid.uuid4())
        band12_id = str(uuid.uuid4())
        print("Updating images")
        update_images(cur, scene, band11_id, band12_id)
        print("Updating bands")
        update_bands(cur, scene, band11_id, band12_id)
        print("Updating scene")
        update_scene(cur, scene)
        conn.commit()
        if scene['ingest_status'] == 'INGESTED':
            print("Re-ingesting scene")
            ingest_scene(scene_id)
    else:
        print("Skipping re-import for Scene: %s . Scene is not broken." % (scene['id']))


def get_scene(cur, scene_id):
    """Fetch scene as using psycopg"""
    cur.execute("""
    SELECT
    s.id, s.ingest_status, s.ingest_location, s.scene_type, s.scene_metadata, s.metadata_files
    FROM scenes s join images on images.scene = s.id
    WHERE s.id = %s""", (scene_id,))
    scene = cur.fetchone()
    return scene


def update_images(cur, scene, band11_id, band12_id):
    """
    Using psycopg:
    Update existing images to have a raw_data_bytes value of 0
    Create new scene images for scene with the same users as the other images
    """
    cur.execute("""
    UPDATE images SET raw_data_bytes = 0 where scene = %s;
    """, (scene["id"],))
    cur.execute("""
        SELECT
        id, created_by, visibility, filename, sourceuri,
        image_metadata, resolution_meters, metadata_files
        FROM images WHERE scene = %s;""",
                (scene["id"],))
    existing_image = cur.fetchone()
    print("Fetched existing image %s", existing_image)
    base_uri = os.path.dirname(existing_image["sourceuri"])
    band11 = {
        "id": band11_id, "created_by": existing_image["created_by"],
        "visibility": existing_image["visibility"], "owner": existing_image["created_by"],
        "filename": "B11.jp2", "sourceuri": base_uri + "/B11.jp2", "scene": scene["id"], "resolution_meters": "20"
    }
    band12 = {
        "id": band12_id, "created_by": existing_image["created_by"],
        "visibility": existing_image["visibility"],
        "filename": "B12.jp2", "sourceuri": base_uri + "/B12.jp2", "scene": scene["id"], "resolution_meters": "20"
    }
    bands = (band11, band12)
    print("Inserting images: %s %s" % bands)
    cur.executemany("""
    INSERT INTO images (
    id, created_at, modified_at, created_by, modified_by,
    owner, raw_data_bytes, visibility, filename, sourceuri,
    scene, resolution_meters, metadata_files, image_metadata
    ) values (
    %(id)s, now(), now(), %(created_by)s, %(created_by)s,
    %(created_by)s, 0, %(visibility)s, %(filename)s, %(sourceuri)s,
    %(scene)s, %(resolution_meters)s, '{}', '{}'
    );
    """, bands)


def update_bands(cur, scene, band11_id, band12_id):
    """Update the bands so ingest work correctly"""
    bands = (
        {
            "id": str(uuid.uuid4()), "image_id": band11_id, "name": "short-wave infrared - 11",
            "wavelength": "{1565,1655}"
        }, {
            "id": str(uuid.uuid4()), "image_id": band12_id, "name": "short-wave infrared - 12",
            "wavelength": "{2100,2280}"
        }
    )
    print("Inserting bands %s %s" % bands)
    cur.executemany("""
    INSERT INTO bands (id, image_id, name, number, wavelength)
    VALUES (%(id)s, %(image_id)s, %(name)s, 0, %(wavelength)s)
    """, bands)


def update_scene(cur, scene):
    """Update band metadata for scene using psycopg
       For ingested scenes, change the ingest status back to uningested
       and the scene_type to COG if necessary. We will be re-ingesting avro
       scenes as COGs

    Args:
        scene (Scene): Scene to update
    """
    cur.execute("""
    UPDATE SCENES
    SET scene_type = 'COG', ingest_status = 'NOTINGESTED', ingest_location = null
    WHERE id = %s""", (scene["id"],))


def check_if_broken(cur, scene_id):
    """If the scene has 11 images instead of 13"""
    cur.execute("""
    SELECT count(id) from images where scene = %s
    """, (scene_id,))
    image_count = cur.fetchone()[0]
    print("Scene has %s images" % (image_count,))
    return image_count < 13
