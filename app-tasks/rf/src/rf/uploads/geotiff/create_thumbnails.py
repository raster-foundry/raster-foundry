"""Creates thumbnails for geotiff imagery

Depends on having geotiff file already downloaded
"""
import os
import subprocess

import boto3

from rf.models import Thumbnail

from .io import get_geotiff_dimensions

def create_thumbnails(tif_path, scene_id, organization_id):
    """Creates thumbnails based on a geotiff path and scene

    Args:
        tif_path (str): path of local tiff file
        scene_id (str): uuid of the scene

    Returns:
        List[Thumbnail]
    """

    r_tif_path = os.path.join(
        os.path.dirname(tif_path),
        'resampled_{}'.format(os.path.basename(tif_path))
    )
    rp_tif_path = os.path.join(
        os.path.dirname(tif_path),
        'reprojected_{}'.format(os.path.basename(tif_path))
    )

    dim = get_geotiff_dimensions(tif_path)
    max_dim = float(max(get_geotiff_dimensions(tif_path)))

    size_large = 1024
    size_small = 256

    scale_large = size_large / max_dim
    scale_small = size_small / max_dim

    dim_large = (int(dim[0] * scale_large), int(dim[1] * scale_large))
    dim_small = (int(dim[0] * scale_small), int(dim[1] * scale_small))

    # Create paths for each size thumb
    path_large = '{}-LARGE.png'.format(scene_id)
    path_small = '{}-SMALL.png'.format(scene_id)

    # Create urls for each size Thumbnail
    url_large = '/thumbnails/{}'.format(path_large)
    url_small = '/thumbnails/{}'.format(path_small)

    try_to_remove_files([r_tif_path, rp_tif_path, path_large, path_small])

    if os.path.isfile(tif_path):
        try:
            # Resample tif
            subprocess.check_call([
                'gdalwarp', tif_path, r_tif_path,
                '-ts', str(dim_large[0]), str(dim_large[1]),
                '-q'
            ])

            # Reproject tif
            subprocess.check_call([
                'gdalwarp', r_tif_path, rp_tif_path,
                '-t_srs', 'EPSG:3857',
                '-q'
            ])

            # Create a temporary env object
            mod_env = os.environ.copy()
            # Add variable to avoid sidecar files
            mod_env['GDAL_PAM_ENABLED'] = 'NO'

            # Convert tif to pngs (large)
            subprocess.check_call([
                'gdal_translate', rp_tif_path, path_large,
                '-b', '1',
                '-b', '2',
                '-b', '3',
                '-outsize', str(dim_large[0]), str(dim_large[1]),
                '-of', 'PNG',
                '-q'
            ], env=mod_env)

            # Convert tif to pngs (small)
            subprocess.check_call([
                'gdal_translate', rp_tif_path, path_small,
                '-b', '1',
                '-b', '2',
                '-b', '3',
                '-outsize', str(dim_small[0]), str(dim_small[1]),
                '-of', 'PNG',
                '-q',
            ], env=mod_env)
        except:
            # If any subprocess calls fail, we need to clean up before exiting
            try_to_remove_files([r_tif_path, rp_tif_path, path_large, path_small])
            raise

        if os.path.isfile(path_large) and os.path.isfile(path_small):
            s3_bucket_name = os.getenv('THUMBNAIL_BUCKET')
            s3_bucket = boto3.resource('s3').Bucket(s3_bucket_name)
            s3_bucket.upload_file(path_large, path_large, {'ContentType': 'image/png'})
            s3_bucket.upload_file(path_small, path_small, {'ContentType': 'image/png'})

        try_to_remove_files([r_tif_path, rp_tif_path, path_large, path_small])

    else:
        return

    # Return List[Thumbnail]
    return [
        Thumbnail(
            organization_id,
            dim_small[0],
            dim_small[1],
            'SMALL',
            url_small,
            sceneId=scene_id
        ),
        Thumbnail(
            organization_id,
            dim_large[0],
            dim_large[1],
            'LARGE',
            url_large,
            sceneId=scene_id
        )
    ]

def try_to_remove_files(files):
    for f in files:
        try_to_remove(f)

def try_to_remove(path):
    try:
        os.remove(path)
    except OSError:
        pass
