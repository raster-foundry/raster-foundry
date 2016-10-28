"""Python class representation of a Raster Foundry Scene"""

import uuid


from .base import BaseModel


class Scene(BaseModel):

    URL_PATH = '/api/scenes/'

    def __init__(self, organizationId, ingestSizeBytes, visibility, tags,
                 datasource, sceneMetadata, name, thumbnailStatus, boundaryStatus,
                 status, metadataFiles, sunAzimuth=None, sunElevation=None, cloudCover=None, acquisitionDate=None,
                 id=None, thumbnails=None, footprint=None, images=None):
        """Create a new Scene

        Args:
            orgnizationId (str): UUID of organization that this scene belongs to
            ingestSizeBytes (int): size of ingested (through geotrellis) scene
            visibility (str): level of access to search for/view scene
            tags (List[str]): list of tags for scene
            datasource (str): datasource that scene belongs to
            sceneMetadata (dict): extra metadata associated with scene
            name (str): name of scene (displayed to users)
            thumbnailStatus (str): status of thumbnail creation
            boundaryStatus (str): status of creating boundaries
            status (str): overall status of scene creation
            sunAzimuth (float): azimuth of sun when scene was created from satellite/uav
            sunElevation (float): elevation of sun when scene was created
            cloudCover (float): percent of scene covered by clouds
            acquisitionDate (datetime): date when scene was acquired
            id (str): UUID primary key for scene
            thumbnails (List[Thumbnail]): list of thumbnails associated with scene
            footprint (Footprint): footprint associated with scene
            images (List[Image]): list of images associated with scene
        """

        self.organizationId = organizationId
        self.ingestSizeBytes = ingestSizeBytes
        self.visibility = visibility
        self.tags = tags
        self.datasource = datasource
        self.sceneMetadata = sceneMetadata
        self.name = name
        self.thumbnailStatus = thumbnailStatus
        self.boundaryStatus = boundaryStatus
        self.status = status
        self.metadataFiles = metadataFiles

        # Optional - can be None
        self.sunAzimuth = sunAzimuth
        self.sunElevation = sunElevation
        self.cloudCover = cloudCover
        self.acquisitionDate = acquisitionDate
        self.id = id or str(uuid.uuid4())
        self.thumbnails = thumbnails
        self.footprint = footprint
        self.images = images

    def __repr__(self):
        return '<Scene: {}>'.format(self.name)

    @classmethod
    def from_dict(cls, d):
        return cls(
            d.get('organizationId'), d.get('ingestSizeBytes'), d.get('visibility'),
            d.get('tags'), d.get('datasource'), d.get('sceneMetadata'), d.get('name'), d.get('thumbnailStatus'),
            d.get('boundaryStatus'), d.get('status'), d.get('sunAzimuth'), d.get('sunElevation'),
            d.get('cloudCover'), d.get('acquisitionDate'), d.get('id')
        )

    def to_dict(self):
        scene_dict = dict(
            organizationId=self.organizationId, ingestSizeBytes=self.ingestSizeBytes, visibility=self.visibility,
            tags=self.tags, datasource=self.datasource, sceneMetadata=self.sceneMetadata,
            name=self.name, thumbnailStatus=self.thumbnailStatus, boundaryStatus=self.boundaryStatus,
            status=self.status, metadataFiles=self.metadataFiles)
        if self.sunAzimuth:
            scene_dict['sunAzimuth'] = self.sunAzimuth
        if self.sunElevation:
            scene_dict['sunElevation'] = self.sunElevation
        if self.cloudCover is not None:
            scene_dict['cloudCover'] = self.cloudCover
        if self.acquisitionDate:
            scene_dict['acquisitionDate'] = self.acquisitionDate
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

        if self.footprint:
            scene_dict['footprint'] = self.footprint.to_dict()

        return scene_dict
