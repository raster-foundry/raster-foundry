"""Python class representation of a Raster Foundry Scene"""

import uuid
from requests.exceptions import HTTPError
import logging

from .base import BaseModel
from .thumbnail import Thumbnail
from .image import Image
from .footprint import Footprint

logger = logging.getLogger(__name__)


class Scene(BaseModel):

    URL_PATH = '/api/scenes/'

    def __init__(self, visibility, tags,
                 datasource, sceneMetadata, name, thumbnailStatus, boundaryStatus,
                 ingestStatus, metadataFiles, sunAzimuth=None, sunElevation=None,
                 cloudCover=None, acquisitionDate=None, id=None, thumbnails=None,
                 tileFootprint=None, dataFootprint=None, images=None, createdAt=None,
                 modifiedAt=None, createdBy=None, modifiedBy=None, ingestLocation=None,
                 owner=None, sceneType="AVRO"):
        """Create a new Scene

        Args:
            visibility (str): level of access to search for/view scene
            tags (List[str]): list of tags for scene
            datasource (str): datasource that scene belongs to
            sceneMetadata (dict): extra metadata associated with scene
            name (str): name of scene (displayed to users)
            thumbnailStatus (str): status of thumbnail creation
            boundaryStatus (str): status of creating boundaries
            ingestStatus (str): overall status of scene creation
            sunAzimuth (float): azimuth of sun when scene was created from satellite/uav
            sunElevation (float): elevation of sun when scene was created
            cloudCover (float): percent of scene covered by clouds
            acquisitionDate (datetime): date when scene was acquired
            id (str): UUID primary key for scene
            thumbnails (List[Thumbnail]): list of thumbnails associated with scene
            tileFootprint (Footprint): footprint of the tile associated with scene
            dataFootprint (Footprint): footprint of this scene's data
            images (List[Image]): list of images associated with scene
            owner (str): user that owns a scene
        """

        self.ingestLocation = ingestLocation
        self.modifiedBy = modifiedBy
        self.createdBy = createdBy
        self.visibility = visibility
        self.tags = tags
        self.datasource = datasource
        self.sceneMetadata = sceneMetadata
        self.name = name
        self.thumbnailStatus = thumbnailStatus
        self.boundaryStatus = boundaryStatus
        self.ingestStatus = ingestStatus
        self.metadataFiles = metadataFiles
        self.owner = owner
        self.sceneType = sceneType

        # Optional - can be None
        self.sunAzimuth = sunAzimuth
        self.sunElevation = sunElevation
        self.cloudCover = cloudCover
        self.acquisitionDate = acquisitionDate
        self.id = id or str(uuid.uuid4())
        self.thumbnails = thumbnails
        self.tileFootprint = tileFootprint
        self.dataFootprint = dataFootprint
        self.images = images
        self.createdAt = createdAt
        self.modifiedAt = modifiedAt

    def __repr__(self):
        return '<Scene: {}>'.format(self.name)

    @classmethod
    def from_dict(cls, d):
        statuses = d.get('statusFields')
        filter_fields = d.get('filterFields')
        images = [Image.from_dict(image) for image in d.get('images')]
        thumbnails = [Thumbnail.from_dict(thumbnail) for thumbnail in d.get('thumbnails')]

        tile_footprint_dict = d.get('tileFootprint')
        data_footprint_dict = d.get('dataFootprint')

        if tile_footprint_dict:
            tile_footprint = Footprint.from_dict(tile_footprint_dict)
        else:
            tile_footprint = None
        if data_footprint_dict:
            data_footprint = Footprint.from_dict(data_footprint_dict)
        else:
            data_footprint = None

        return cls(
            d.get('visibility'),
            d.get('tags'), d.get('datasource')['id'], d.get('sceneMetadata'), d.get('name'), statuses.get('thumbnailStatus'),
            statuses.get('boundaryStatus'), statuses.get('ingestStatus'), d.get('metadataFiles'),
            filter_fields.get('sunAzimuth'), filter_fields.get('sunElevation'), filter_fields.get('cloudCover'),
            filter_fields.get('acquisitionDate'), d.get('id'), thumbnails, tile_footprint, data_footprint,
            images, d.get('createdAt'), d.get('modifiedAt'), d.get('createdBy'), d.get('modifiedBy'),
            d.get('ingestLocation', ''), owner=d.get('owner'), sceneType=d.get('sceneType')
        )

    def to_dict(self):
        filterFields = {}
        statusFields = dict(thumbnailStatus=self.thumbnailStatus,
                            boundaryStatus=self.boundaryStatus,
                            ingestStatus=self.ingestStatus)
        scene_dict = dict(
            visibility=self.visibility,
            tags=self.tags, datasource=self.datasource, sceneMetadata=self.sceneMetadata, filterFields=filterFields,
            name=self.name, statusFields=statusFields, metadataFiles=self.metadataFiles,
            ingestLocation=self.ingestLocation, owner=self.owner, sceneType=self.sceneType)

        if self.sunAzimuth:
            filterFields['sunAzimuth'] = self.sunAzimuth
        if self.sunElevation:
            filterFields['sunElevation'] = self.sunElevation
        if self.cloudCover is not None:
            filterFields['cloudCover'] = self.cloudCover
        if self.acquisitionDate:
            filterFields['acquisitionDate'] = self.acquisitionDate
        if self.id:
            scene_dict['id'] = self.id

        if self.thumbnails:
            scene_dict['thumbnails'] = [thumbnail.to_dict() for thumbnail in self.thumbnails]
        else:
            scene_dict['thumbnails'] = []

        if self.images:
            scene_dict['images'] = [image.to_dict() for image in self.images]
        else:
            scene_dict['images'] = []

        if self.tileFootprint:
            scene_dict['tileFootprint'] = self.tileFootprint.to_dict()
        if self.dataFootprint:
            scene_dict['dataFootprint'] = self.dataFootprint.to_dict()

        if self.createdAt:
            scene_dict['createdAt'] = self.createdAt

        if self.modifiedAt:
            scene_dict['modifiedAt'] = self.modifiedAt

        if self.modifiedBy:
            scene_dict['modifiedBy'] = self.modifiedBy

        if self.createdBy:
            scene_dict['createdBy'] = self.createdBy

        return scene_dict

    def create(self):
        try:
            return super(Scene, self).create()
        except HTTPError as exc:
            if exc.response.status_code != 409:
                raise
            else:
                logger.info('Tried to create duplicate object: %s', self)
                return None

    def get_extent(self):
        """Helper method to return extent of scene"""

        assert self.tileFootprint, 'Must have tile footprint to extract extent'
        coords = self.tileFootprint.multipolygon[0][0]
        longitudes = [coord[0] for coord in coords]
        latitudes = [coord[1] for coord in coords]
        return [
            min(longitudes),
            min(latitudes),
            max(longitudes),
            max(latitudes)
        ]
