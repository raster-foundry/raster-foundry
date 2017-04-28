# Factories to create scenes from different geotiff upload destinations
import os
import tempfile

import boto3

from .create_thumbnails import create_thumbnails
from .create_images import create_geotiff_image
from .create_scenes import create_geotiff_scene

from .io import s3_url, s3_bucket_and_key_from_url
from rf.utils.io import Visibility

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
        self.files = self._upload.files
        self.owner = upload.owner
        self.organizationId = self._upload.organizationId
        self.visibility = Visibility.PRIVATE
        self.datasource = self._upload.datasource
        self.acquisitionDate = self._upload.metadata.get('acquisitionDate')
        self.tags = self._upload.metadata.get('tags') or ['']

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
            local_tif = tempfile.NamedTemporaryFile(delete=False)
            try:
                bucket_name, key = s3_bucket_and_key_from_url(infile)
                bucket = s3.Bucket(bucket_name)
                bucket.download_file(key, local_tif.name)
                # We need to override the autodetected filename because we're loading into temp
                # files which don't preserve the file name that is on S3.
                filename = os.path.basename(key)
                scene = self.create_geotiff_scene(local_tif.name, os.path.splitext(filename)[0])
                image = self.create_geotiff_image(local_tif.name, infile,
                                                  scene, filename)

                # TODO: thumbnails aren't currently created in a way that matches serialization
                # in the API
                scene.thumbnails = create_thumbnails(local_tif.name, scene.id, self.organizationId)
                scene.images = [image]
            finally:
                os.remove(local_tif.name)
            yield scene

    def create_geotiff_image(self, tif_path, source_uri, scene, filename):
        return create_geotiff_image(self.organizationId, tif_path, source_uri, scene=scene.id,
                                    filename=filename, visibility=self.visibility, owner=self.owner)

    def create_geotiff_scene(self, tif_path, name):
        return create_geotiff_scene(
            tif_path,
            self.organizationId,
            self.datasource,
            visibility=self.visibility,
            tags=self.tags,
            acquisitionDate=self.acquisitionDate,
            name=name,
            owner=self.owner
        )
