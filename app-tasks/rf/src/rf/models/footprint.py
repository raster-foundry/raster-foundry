"""Python class representation of a Raster Foundry footprint"""

from .base import BaseModel


class Footprint(BaseModel):

    URL_PATH = '/api/footprints/'

    def __init__(self, organizationId, multipolygon, id=None, sceneId=None, createdAt=None, modifiedAt=None):
        """Create a new Footprint

        Args:
            orgnizationId (str): UUID of organization that this scene belongs to
            multipolygon (dict): geojson for footprint
            id (str): UUID for footprint
            createdAt (str): when object was created
            modifiedAt (str): when object was last modified
        """
        self.organizationId = organizationId
        self.multipolygon = multipolygon

        # Optional - Can be none
        self.id = id
        self.sceneId = sceneId
        self.createdAt = createdAt
        self.modifiedAt = modifiedAt

    def __repr__(self):
        return '<Footprint: {}>'.format(self.id)

    @classmethod
    def from_dict(cls, d):
        return cls(
            d.get('organizationId'), d.get('coordinates'), d.get('id'), d.get('sceneId'),
            d.get('createdAt'), d.get('modifiedAt')
        )

    def to_dict(self):
        return {'type': 'MultiPolygon', 'coordinates': self.multipolygon}

    def create(self):
        assert self.sceneId, 'Scene ID is required to create a Footprint'
        return super(Footprint, self).create()
