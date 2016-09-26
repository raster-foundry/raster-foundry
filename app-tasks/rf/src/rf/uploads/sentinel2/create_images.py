"""Creates images based on tileinfo and resolution requested"""

import logging

from rf.utils.io import Visibility
from rf.models import Image

from .settings import s3, organization, base_http_path, bucket

logger = logging.getLogger(__name__)


def create_images(tileinfo, resolution_meters):
    """Return images for tile based on resolution requested

    This function determines which images belong to a scene based on
    the resoluton because Sentinel 2 images have a single band and their
    resolution depends on what band is in the image

    Args:
        tileinfo (dict): dictionary represenation of tileInfo.json
        resolution_meters (int): resolution of images requested

    Returns:
        List[Image]
    """

    tileinfo_path = tileinfo['path']
    logger.info('Creating images for %s with resolution %s', tileinfo_path, resolution_meters)

    def image_from_key(key):
        """Return Image based on s3 key

        Args:
            key (s3.Object): s3 object representation of an image

        Returns:
            Image
        """
        filename = key.key.split("/")[-1]
        return Image(
            organization,
            key.content_length,
            Visibility.PUBLIC,
            filename,
            base_http_path.format(key_path=key.key),
            bands=[filename.split('.')[0]],
            imageMetadata={'resolutionMeters': resolution_meters}
        )

    if resolution_meters == 10:
        keys = [
            s3.Object(bucket.name, '{tileinfo_path}/B02.jp2'.format(tileinfo_path=tileinfo_path)),
            s3.Object(bucket.name, '{tileinfo_path}/B03.jp2'.format(tileinfo_path=tileinfo_path)),
            s3.Object(bucket.name, '{tileinfo_path}/B04.jp2'.format(tileinfo_path=tileinfo_path)),
            s3.Object(bucket.name, '{tileinfo_path}/B08.jp2'.format(tileinfo_path=tileinfo_path))
        ]
    elif resolution_meters == 20:
        keys = [
            s3.Object(bucket.name, '{tileinfo_path}/B05.jp2'.format(tileinfo_path=tileinfo_path)),
            s3.Object(bucket.name, '{tileinfo_path}/B06.jp2'.format(tileinfo_path=tileinfo_path)),
            s3.Object(bucket.name, '{tileinfo_path}/B07.jp2'.format(tileinfo_path=tileinfo_path)),
            s3.Object(bucket.name, '{tileinfo_path}/B8A.jp2'.format(tileinfo_path=tileinfo_path))
        ]
    elif resolution_meters == 60:
        keys = [
            s3.Object(bucket.name, '{tileinfo_path}/B01.jp2'.format(tileinfo_path=tileinfo_path)),
            s3.Object(bucket.name, '{tileinfo_path}/B09.jp2'.format(tileinfo_path=tileinfo_path)),
            s3.Object(bucket.name, '{tileinfo_path}/B10.jp2'.format(tileinfo_path=tileinfo_path))
        ]
    else:
        raise NotImplementedError('Unable to create images for {} at resolution {}'.format(
            tileinfo_path, resolution_meters)
        )
    return [image_from_key(key) for key in keys]
