import logging
import uuid

from rf.models import Scene
from rf.utils.io import IngestStatus, JobStatus, Visibility

from .io import get_geotiff_metadata, get_geotiff_name

logger = logging.getLogger(__name__)


def create_geotiff_scene(tif_path, datasource, acquisitionDate=None, cloudCover=0,
                         visibility=Visibility.PRIVATE, tags=[],
                         sceneMetadata=None, name=None, thumbnailStatus=JobStatus.QUEUED,
                         boundaryStatus=JobStatus.QUEUED, ingestStatus=IngestStatus.TOBEINGESTED,
                         metadataFiles=[], owner=None, sceneType="COG", **kwargs):
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

    sceneKwargs = {
        'sunAzimuth': None,  # TODO: Calculate from acquisitionDate and tif center.
        'sunElevation': None,  # TODO: Same
        'cloudCover': cloudCover,
        'acquisitionDate': acquisitionDate,
        'id': str(uuid.uuid4()),
        'thumbnails': None
    }
    # Override defaults with kwargs
    sceneKwargs.update(kwargs)

    # Construct Scene
    scene = Scene(
        visibility,
        tags,
        datasource,
        sceneMetadata,
        name,
        thumbnailStatus,
        boundaryStatus,
        ingestStatus,
        metadataFiles,
        owner=owner,
        sceneType=sceneType,
        **sceneKwargs
    )

    return scene
