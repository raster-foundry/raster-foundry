"""Python class representation of a Raster Foundry Scene"""

from .base import BaseModel

class Scene(BaseModel):

    URL_PATH = '/api/scenes/'

    def __init__(self, organizationId, ingestSizeBytes, visibility, resolutionMeters,
                 tags, datasource, sceneMetadata, name, thumbnailStatus, boundaryStatus,
                 status, sunAzimuth=None, sunElevation=None, cloudCover=None, acquisitionDate=None,
                 id=None, createdBy=None, modifiedBy=None, createdAt=None, modifiedAt=None, thumbnails=None,
                 footprint=None, images=None):
        """Create a new Scene

        Args:
            orgnizationId (str): UUID of organization that this scene belongs to
            ingestSizeBytes (int): size of ingested (through geotrellis) scene
            visibility (str): level of access to search for/view scene
            resolutionMeters (float): resolution of scene
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
            createdBy (str): user that created scene
            modifiedBy (str): user that modified scene
            createdAt (str): time scene was created
            modifiedAt (str): time when scene was last modified
            thumbnails (List[Thumbnail]): list of thumbnails associated with scene
            footprint (Footprint): footprint associated with scene
            images (List[Image]): list of images associated with scene
        """

        self.organizationId = organizationId
        self.ingestSizeBytes = ingestSizeBytes
        self.visibility = visibility
        self.resolutionMeters = resolutionMeters
        self.tags = tags
        self.datasource = datasource
        self.sceneMetadata = sceneMetadata
        self.name = name
        self.thumbnailStatus = thumbnailStatus
        self.boundaryStatus = boundaryStatus
        self.status = status

        # Optional - can be None
        self.sunAzimuth = sunAzimuth
        self.sunElevation = sunElevation
        self.cloudCover = cloudCover
        self.acquisitionDate = acquisitionDate
        self.id = id
        self.createdBy = createdBy
        self.modifiedBy = modifiedBy
        self.createdAt = createdAt
        self.modifiedAt = modifiedAt
        self.thumbnails = thumbnails
        self.footprint = footprint
        self.images = images

    def __repr__(self):
        return '<Scene: {}>'.format(self.name)

    @classmethod
    def from_dict(cls, d):
        cloudCover = d.get('cloudCover') or d.get('cloudyPixelPercentage')
        return cls(
            d.get('organizationId'), d.get('ingestSizeBytes'), d.get('visibility'), d.get('resolutionMeters'),
            d.get('tags'), d.get('datasource'), d.get('sceneMetadata'), d.get('name'), d.get('thumbnailStatus'),
            d.get('boundaryStatus'), d.get('status'), d.get('sunAzimuth'), d.get('sunElevation'), cloudCover,
            d.get('acquisitionDate'), d.get('id'), d.get('createdBy'), d.get('modifiedBy'), d.get('createdAt'),
            d.get('modifiedAt')
        )

    def to_dict(self):
        scene_dict =  dict(
            organizationId=self.organizationId, ingestSizeBytes=self.ingestSizeBytes, visibility=self.visibility,
            resolutionMeters=self.resolutionMeters, tags=self.tags, datasource=self.datasource, sceneMetadata=self.sceneMetadata,
            name=self.name, thumbnailStatus=self.thumbnailStatus, boundaryStatus=self.boundaryStatus, status=self.status)
        if self.sunAzimuth:
            scene_dict['sunAzimuth'] = self.sunAzimuth
        if self.sunElevation:
            scene_dict['sunElevation'] = self.sunElevation
        if self.cloudCover:
            scene_dict['cloudCover'] = self.cloudCover
        if self.acquisitionDate:
            scene_dict['acquisitionDate'] = self.acquisitionDate
        if self.id:
            scene_dict['id'] = self.id
        if self.createdBy:
            scene_dict['createdBy'] = self.createdBy
        if self.modifiedBy:
            scene_dict['modifiedBy'] = self.modifiedBy
        if self.createdAt:
            scene_dict['createdAt'] = self.createdAt
        if self.modifiedAt:
            scene_dict['modifiedAt'] = self.modifiedAt

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
