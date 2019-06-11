# Factories to create scenes from geotiffs on disk somewhere
import logging
import os

import boto3

from .create_images import create_geotiff_image
from .create_scenes import create_geotiff_scene
from rf.utils import cog
from rf.utils.io import (
    Visibility,
    IngestStatus,
    upload_tifs,
    s3_bucket_and_key_from_url,
    get_tempdir
)

import urllib

logger = logging.getLogger(__name__)


class GeoTiffS3SceneFactory(object):
    """A convenience class for creating Scenes from an S3 folder of multiband GeoTiffs.

    Example usage:
    ```
        from rf.utils.io import Visibility

        captureDate = datetime.date(YYYY, MM, DD)

        factory = GeoTiffS3SceneFactory('<Upload Here>')
        for scene in factory.generate_scenes():
            # do something with the created scenes
            # Note that this will download GeoTIFFs locally, so it is best run somewhere with a fast
            # connection to S3
    ```
    """
    def __init__(self, upload):
        """Args:
            upload (Upload): instance of upload model to create scenes for
        """
        self._upload = upload
        self.isProjectUpload = upload.projectId is not None
        self.files = self._upload.files
        self.owner = upload.owner
        self.visibility = Visibility.PRIVATE
        self.datasource = self._upload.datasource
        self.acquisitionDate = self._upload.metadata.get('acquisitionDate')
        self.cloudCover = self._upload.metadata.get('cloudCover', 0)
        self.fileType = upload.fileType
        self.tags = self._upload.metadata.get('tags') or ['']
        self.keep_in_source_bucket = self._upload.keepInSourceBucket

    def generate_scenes(self):
        """Create a Scene and associated Image for each GeoTiff in self.s3_path
        Returns:
            Generator of Scenes
        """
        s3 = boto3.resource('s3')
        for infile in self.files:
            # We can't use the temp file as a context manager because it'll be opened/closed multiple
            # times and by default is deleted when it's closed. So we use try/finally to ensure that
            # it gets cleaned up.
            bucket_name, key = s3_bucket_and_key_from_url(infile)
            filename = os.path.basename(key)
            logger.info('Downloading %s => %s', infile, filename)
            bucket = s3.Bucket(bucket_name)
            with get_tempdir() as tempdir:
                tmp_fname = os.path.join(tempdir, filename)
                bucket.download_file(key, tmp_fname)

                if self.fileType == 'NON_SPATIAL':
                    tmp_fname = cog.georeference_file(tmp_fname)

                cog.add_overviews(tmp_fname)
                cog_path = cog.convert_to_cog(tmp_fname, tempdir)
                scene = self.create_geotiff_scene(tmp_fname, os.path.splitext(filename)[0])
                if self.keep_in_source_bucket:
                    scene.ingestLocation = upload_tifs([cog_path], self.owner, scene.id, bucket_name)[0]
                else:
                    scene.ingestLocation = upload_tifs([cog_path], self.owner, scene.id)[0]
                images = [self.create_geotiff_image(
                    tmp_fname, urllib.unquote(scene.ingestLocation), scene, cog_path
                )]

            scene.thumbnails = []
            scene.images = images
            yield scene

    def create_geotiff_image(self, tif_path, source_uri, scene, filename):
        return create_geotiff_image(tif_path, source_uri, scene=scene.id,
                                    filename=filename, visibility=self.visibility, owner=self.owner)

    def create_geotiff_scene(self, tif_path, name):
        # Always COGs, now and forever
        ingestStatus = IngestStatus.INGESTED
        return create_geotiff_scene(
            tif_path,
            self.datasource,
            visibility=self.visibility,
            tags=self.tags,
            acquisitionDate=self.acquisitionDate,
            cloudCover=self.cloudCover,
            name=name,
            owner=self.owner,
            ingestStatus=ingestStatus,
            sceneType='COG'
        )
