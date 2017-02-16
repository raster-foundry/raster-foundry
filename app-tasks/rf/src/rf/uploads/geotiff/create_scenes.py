import boto3
import logging
import os
import tempfile
import uuid

from rf.models import Scene
from rf.utils.io import IngestStatus, JobStatus, Visibility

from .create_images import create_geotiff_image
from .create_thumbnails import create_thumbnails
from .io import get_geotiff_metadata, get_geotiff_name, s3_url
from .create_footprints import extract_footprints

logger = logging.getLogger(__name__)


# TODO: Add tests once this functionality is tested across a larger number of client tiffs.
class GeoTiffS3SceneFactory(object):
    """A convenience class for creating Scenes from an S3 folder of multiband GeoTiffs.

    Example usage:
    ```
        from rf.utils.io import Visibility

        captureDate = datetime.date(YYYY, MM, DD)

        factory = GeoTiffS3SceneFactory('<Organization ID Here>', Visibility.PRIVATE,
                                        '<data source name here', captureDate,
                                        '<bucket name here>',
                                        '<folder prefix here, e.g. imagery-provider-name/philly>')
        for scene in factory.generate_scenes():
            # do something with the created scenes
            # Note that this will download GeoTIFFs locally, so it is best run somewhere with a fast
            # connection to S3
    ```
    """
    def __init__(self, organizationId, visibility, datasource, acquisitionDate,
                 bucket_name, files_prefix, tags=[]):
        """Args:
            organizationId (str): UUID of Organization that should own generated resources
            visibility (Visibility): Desired visibility of generated resources
            datasource (str): Description of the data source for the GeoTiffs
            acquisitionDate (datetime): The date on which the GeoTiffs were acquired
            bucket_name (str): Name of the bucket in which the GeoTiffs are located
            files_prefix (str): Prefix to "folder" containing GeoTiffs to process
            tags (List[str]): List of tags to apply to generated Scenes
        """
        self.organizationId = organizationId
        self.visibility = visibility
        self.datasource = datasource
        self.acquisitionDate = acquisitionDate
        self.bucket_name = bucket_name
        self.files_prefix = files_prefix
        self.tags = tags

    def generate_scenes(self):
        """Create a Scene and associated Image for each GeoTiff in self.s3_path
        Returns:
            Generator of Scenes
        """
        s3 = boto3.resource('s3')
        bucket = s3.Bucket(self.bucket_name)
        for s3_tif in bucket.objects.filter(Prefix=self.files_prefix):
            # We can't use the temp file as a context manager because it'll be opened/closed multiple
            # times and by default is deleted when it's closed. So we use try/finally to ensure that
            # it gets cleaned up.
            local_tif = tempfile.NamedTemporaryFile(delete=False)
            try:
                bucket.download_file(s3_tif.key, local_tif.name)
                # We need to override the autodetected filename because we're loading into temp
                # files which don't preserve the file name that is on S3.
                filename = os.path.basename(s3_tif.key)
                scene = self.create_geotiff_scene(local_tif.name, os.path.splitext(filename)[0])
                image = self.create_geotiff_image(local_tif.name, s3_url(bucket.name, s3_tif.key),
                                                  scene, filename)


                scene.thumbnails = create_thumbnails(local_tif.name, scene.id, self.organizationId)
                scene.images = [image]
            finally:
                os.remove(local_tif.name)
            yield scene

    def create_geotiff_image(self, tif_path, source_uri, scene, filename):
        return create_geotiff_image(self.organizationId, tif_path, source_uri, scene=scene,
                                    filename=filename, visibility=self.visibility)

    def create_geotiff_scene(self, tif_path, name):
        return create_geotiff_scene(
            tif_path,
            self.organizationId,
            self.datasource,
            visibility=self.visibility,
            tags=self.tags,
            acquisitionDate=self.acquisitionDate,
            name=name
        )


def create_geotiff_scene(tif_path, organizationId, datasource,
                         ingestSizeBytes=0, visibility=Visibility.PRIVATE, tags=[],
                         sceneMetadata=None, name=None, thumbnailStatus=JobStatus.QUEUED,
                         boundaryStatus=JobStatus.QUEUED, ingestStatus=IngestStatus.NOTINGESTED,
                         metadataFiles=[],
                         **kwargs):
    """Returns scenes that can be created via API given a local path to a geotiff.

    Does not create Images because those require a Source URI, which this doesn't know about. Use
    create_geotiff_scene from a higher level of code that knows about where things are located
    remotely and then add those Images to the Scene that this function returns.

    Tries to extract metadata from the GeoTIFF where possible, but can also accept parameter
    overrides. Order of preference is as follows:
        1) Kwargs
        2) GeoTiff Value / Dynamic value (e.g. azimuth calculated from capture location / time)
        3) Default values

    Args:
        tif_path (str): Local path to GeoTIFF file to use.
        organizationId (str): UUID of Organization that should own Scene
        datasource (str): Name describing the source of the data
        **kwargs: Any remaining keyword arguments will override the values being passed to the Scene
            constructor. If

    Returns:
        List[Scene]
    """
    logger.info('Generating Scene from %s', tif_path)

    # Start with default values
    sceneMetadata = sceneMetadata if sceneMetadata else get_geotiff_metadata(tif_path)
    name = name if name else get_geotiff_name(tif_path)

    tile_footprint, data_footprint = extract_footprints(
        organizationId, tif_path
    )

    sceneKwargs = {
        'sunAzimuth': None,  # TODO: Calculate from acquisitionDate and tif center.
        'sunElevation': None,  # TODO: Same
        'cloudCover': 0,
        'acquisitionDate': None,
        'id': str(uuid.uuid4()),
        'thumbnails': None,
        'tileFootprint': tile_footprint,
        'dataFootprint': data_footprint
    }
    # Override defaults with kwargs
    sceneKwargs.update(kwargs)

    # Construct Scene
    scene = Scene(
        organizationId,
        ingestSizeBytes,
        visibility,
        tags,
        datasource,
        sceneMetadata,
        name,
        thumbnailStatus,
        boundaryStatus,
        ingestStatus,
        metadataFiles,
        **sceneKwargs
    )

    return scene
